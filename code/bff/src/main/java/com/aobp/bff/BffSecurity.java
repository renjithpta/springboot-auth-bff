package com.aobp.bff;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.serializer.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/** Makes the BFF a confidential OAuth client; API calls get 401 instead of HTML login redirects. */
@Configuration
public class BffSecurity {
  /** Keeps public authorization URLs distinct from internal token and key endpoints. */
  @Bean
  ClientRegistrationRepository registrations(
      @Value("${app.issuer}") String issuer,
      @Value("${app.auth-internal}") String internal,
      @Value("${app.client-id}") String id,
      @Value("${app.client-secret}") String secret,
      @Value("${app.callback}") String callback) {
    return new InMemoryClientRegistrationRepository(
        ClientRegistration.withRegistrationId("platform")
            .clientId(id)
            .clientSecret(secret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(callback)
            .scope("openid", "profile", "flights.read", "flights.write")
            .issuerUri(issuer)
            .authorizationUri(issuer + "/oauth2/authorize")
            .tokenUri(internal + "/oauth2/token")
            .jwkSetUri(internal + "/oauth2/jwks")
            .userInfoUri(internal + "/userinfo")
            .userNameAttributeName("sub")
            .clientName("AOBP Identity")
            .build());
  }

  /** Uses the same encrypted repository during login, refresh, and logout. */
  @Bean
  OAuth2AuthorizedClientRepository clientRepository(EncryptedClients clients) {
    return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(clients);
  }

  /** Lets Spring validate state/nonce and PKCE while keeping CSRF enabled for session APIs. */
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientRepository repository,
      @Value("${app.home}") String home)
      throws Exception {
    var resolver =
        new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/health/**", "/bff/api/v1/session", "/bff/api/v1/csrf-token")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, x) -> {
                          res.setStatus(401);
                          res.setContentType("application/problem+json");
                          res.getWriter()
                              .write(
                                  "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}");
                        })
                    .accessDeniedHandler(
                        (req, res, x) -> {
                          res.setStatus(403);
                          res.setContentType("application/problem+json");
                          res.getWriter()
                              .write(
                                  "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403}");
                        }))
        .oauth2Login(
            o ->
                o.authorizedClientRepository(repository)
                    .authorizationEndpoint(a -> a.authorizationRequestResolver(resolver))
                    .tokenEndpoint(t -> t.accessTokenResponseClient(codeTokenClient()))
                    .failureHandler(
                        (req, res, error) -> {
                          org.slf4j.LoggerFactory.getLogger("LoginFailure")
                              .warn("Login failed: {}", error.getMessage());
                          res.sendRedirect(home + "?login=failed");
                        })
                    .defaultSuccessUrl(home, true))
        .logout(l -> l.disable());
    return http.build();
  }

  /** Refreshes expiring tokens; controllers serialize refresh operations across replicas. */
  @Bean
  OAuth2AuthorizedClientManager manager(
      ClientRegistrationRepository registrations, OAuth2AuthorizedClientRepository repository) {
    var manager = new DefaultOAuth2AuthorizedClientManager(registrations, repository);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder()
            .authorizationCode()
            .refreshToken(r -> r.accessTokenResponseClient(refreshTokenClient()))
            .build());
    return manager;
  }

  /** Limits token HTTP calls so a stalled issuer cannot outlive the refresh lock lease. */
  private org.springframework.web.client.RestTemplate tokenHttp() {
    var factory =
        new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    var http =
        new org.springframework.web.client.RestTemplate(
            java.util.List.of(
                new org.springframework.http.converter.FormHttpMessageConverter(),
                new org.springframework.security.oauth2.core.http.converter
                    .OAuth2AccessTokenResponseHttpMessageConverter()));
    http.setRequestFactory(factory);
    http.setErrorHandler(
        new org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler());
    return http;
  }

  /** Uses the same bounded transport for authorization-code exchange. */
  private org.springframework.security.oauth2.client.endpoint
          .DefaultAuthorizationCodeTokenResponseClient
      codeTokenClient() {
    var client =
        new org.springframework.security.oauth2.client.endpoint
            .DefaultAuthorizationCodeTokenResponseClient();
    client.setRestOperations(tokenHttp());
    return client;
  }

  /** Uses the same bounded transport for refresh-token exchange. */
  private org.springframework.security.oauth2.client.endpoint.DefaultRefreshTokenTokenResponseClient
      refreshTokenClient() {
    var client =
        new org.springframework.security.oauth2.client.endpoint
            .DefaultRefreshTokenTokenResponseClient();
    client.setRestOperations(tokenHttp());
    return client;
  }

  /** Encrypts complete Redis sessions, which can also contain an OIDC ID token. */
  @Bean(name = "springSessionDefaultRedisSerializer")
  RedisSerializer<Object> sessionSerializer(TokenCipher cipher) {
    var delegate = new JdkSerializationRedisSerializer();
    return new RedisSerializer<>() {
      public byte[] serialize(Object value) {
        return value == null ? null : cipher.encrypt(delegate.serialize(value));
      }

      public Object deserialize(byte[] bytes) {
        return bytes == null || bytes.length == 0
            ? null
            : delegate.deserialize(cipher.decrypt(bytes));
      }
    };
  }
}
