package com.aobp.flightservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

/** Validates the application issuer and audience even when the caller is the trusted BFF. */
@Configuration
public class ApiSecurity {
  /**
   * Installs stateless bearer-token authentication; CSRF is unnecessary without cookie
   * authentication.
   */
  @Bean
  SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a -> a.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> {}))
        .build();
  }

  /** Requires issuer, timestamps, and the exact intended API audience. */
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${app.issuer}") String issuer,
      @Value("${app.jwks}") String jwks,
      @Value("${app.audience}") String audience) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    OAuth2TokenValidator<Jwt> aud =
        token ->
            token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Wrong audience", null));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), aud));
    return decoder;
  }
}
