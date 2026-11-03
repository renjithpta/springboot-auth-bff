package com.aobp.authserver;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.*;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/** Separates protocol endpoints from human login and issues only application-owned identities. */
@Configuration
public class AuthorizationConfig {
  /** Installs OAuth2 and OIDC protocol endpoints using framework validation. */
  @Bean
  @Order(1)
  SecurityFilterChain protocol(HttpSecurity http) throws Exception {
    var server = OAuth2AuthorizationServerConfigurer.authorizationServer();
    http.securityMatcher(server.getEndpointsMatcher())
        .with(server, c -> c.oidc(Customizer.withDefaults()))
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .exceptionHandling(
            e ->
                e.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
    return http.build();
  }

  /** Enables local login and conditionally enables configured upstream OIDC providers. */
  @Bean
  @Order(2)
  SecurityFilterChain login(
      HttpSecurity http,
      org.springframework.beans.factory.ObjectProvider<ClientRegistrationRepository> providers,
      IdentityStore users)
      throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/login", "/assets/**", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(f -> f.loginPage("/login").permitAll());
    if (providers.getIfAvailable() != null) {
      var resolver =
          new org.springframework.security.oauth2.client.web
              .DefaultOAuth2AuthorizationRequestResolver(
              providers.getObject(), "/oauth2/authorization");
      resolver.setAuthorizationRequestCustomizer(
          org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
              .withPkce());
      http.oauth2Login(
          o ->
              o.loginPage("/login")
                  .authorizationEndpoint(a -> a.authorizationRequestResolver(resolver))
                  .userInfoEndpoint(
                      u ->
                          u.oidcUserService(
                              request -> {
                                var upstream = new OidcUserService().loadUser(request);
                                if (upstream.getClaims().containsKey("app_user"))
                                  throw new org.springframework.security.oauth2.core
                                      .OAuth2AuthenticationException("reserved_claim_collision");
                                String name =
                                    users.externalUser(
                                        upstream.getIssuer().toString(), upstream.getSubject());
                                var claims = new HashMap<String, Object>(upstream.getClaims());
                                claims.put("app_user", name);
                                // Keep the verified upstream token unchanged; use separate UserInfo
                                // claims for the internal name.
                                return new DefaultOidcUser(
                                    users.authorities(name),
                                    upstream.getIdToken(),
                                    new OidcUserInfo(claims),
                                    "app_user");
                              })));
    }
    return http.build();
  }

  /** Uses salted adaptive password hashes for local accounts and confidential clients. */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  /** Registers the BFF with exact redirects and mandatory PKCE; persistence survives restarts. */
  @Bean
  @DependsOn("identitySchema")
  RegisteredClientRepository clients(
      JdbcTemplate jdbc,
      PasswordEncoder encoder,
      @Value("${app.bff-client-id}") String id,
      @Value("${app.bff-secret}") String secret,
      @Value("${app.bff-callback}") String callback,
      @Value("${app.bff-logout}") String logout,
      @Value("${app.access-ttl}") Duration access,
      @Value("${app.refresh-ttl}") Duration refresh) {
    var repository = new JdbcRegisteredClientRepository(jdbc);
    var current = repository.findByClientId(id);
    repository.save(
        RegisteredClient.withId(current == null ? UUID.randomUUID().toString() : current.getId())
            .clientId(id)
            .clientSecret(encoder.encode(secret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri(callback)
            .postLogoutRedirectUri(logout)
            .scope("openid")
            .scope("profile")
            .scope("flights.read")
            .scope("flights.write")
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(false)
                    .build())
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenTimeToLive(access)
                    .refreshTokenTimeToLive(refresh)
                    .reuseRefreshTokens(false)
                    .build())
            .build());
    return repository;
  }

  /** Persists grants and refresh-token state rather than using in-memory sample repositories. */
  @Bean
  @DependsOn("identitySchema")
  OAuth2AuthorizationService authorizations(JdbcTemplate jdbc, RegisteredClientRepository clients) {
    return new JdbcOAuth2AuthorizationService(jdbc, clients);
  }

  /** Persists consent decisions independently from active token records. */
  @Bean
  @DependsOn("identitySchema")
  OAuth2AuthorizationConsentService consents(
      JdbcTemplate jdbc, RegisteredClientRepository clients) {
    return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
  }

  /** Uses a stable public issuer regardless of internal container addresses. */
  @Bean
  AuthorizationServerSettings settings(@Value("${app.issuer}") String issuer) {
    return AuthorizationServerSettings.builder().issuer(issuer).build();
  }

  /** Loads a persistent PKCS8 RSA private key; startup never silently replaces signing keys. */
  @Bean
  JWKSource<SecurityContext> keys(@Value("${app.signing-key}") String file) throws Exception {
    String pem =
        Files.readString(Path.of(file))
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    var factory = KeyFactory.getInstance("RSA");
    var key =
        (RSAPrivateCrtKey)
            factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    var pub =
        (RSAPublicKey)
            factory.generatePublic(
                new java.security.spec.RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
    var rsa = new RSAKey.Builder(pub).privateKey(key).build();
    rsa = new RSAKey.Builder(rsa).keyID(rsa.computeThumbprint().toString()).build();
    var set = new JWKSet(rsa);
    return (selector, context) -> selector.select(set);
  }

  /** Supports OIDC ID-token and UserInfo validation. */
  @Bean
  JwtDecoder decoder(JWKSource<SecurityContext> source) {
    return org.springframework.security.oauth2.server.authorization.config.annotation.web
        .configuration.OAuth2AuthorizationServerConfiguration.jwtDecoder(source);
  }

  /**
   * Emits current application entitlements for both local and explicitly linked external
   * identities.
   */
  @Bean
  OAuth2TokenCustomizer<JwtEncodingContext> claims(IdentityStore users) {
    return context -> {
      if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        String name = context.getPrincipal().getName();
        if (!users.loadUserByUsername(name).isEnabled())
          throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
              "account_disabled");
        context
            .getClaims()
            .audience(new ArrayList<>(List.of("flight-api")))
            .claim("roles", new ArrayList<>(users.authorities(name).stream().map(a -> a.getAuthority()).toList()))
            .claim("terminals", users.terminals(name));
      }
    };
  }
}
