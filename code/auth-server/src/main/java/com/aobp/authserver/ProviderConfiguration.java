package com.aobp.authserver;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;

/** Adds any number of OIDC providers from configuration without recompiling the application. */
@Configuration
@EnableConfigurationProperties(ProviderConfiguration.Providers.class)
public class ProviderConfiguration {
  @ConfigurationProperties("app.federation")
  public record Providers(boolean enabled, Map<String, Provider> providers) {}

  public record Provider(
      String name,
      String clientId,
      String clientSecret,
      String clientAuthenticationMethod,
      String issuer,
      String authorizationUri,
      String tokenUri,
      String jwksUri,
      String userInfoUri,
      String callback) {}

  /**
   * Requires explicit metadata URLs so browser-facing and private backchannel addresses can differ.
   */
  @Bean
  @ConditionalOnProperty(name = "app.federation.enabled", havingValue = "true")
  ClientRegistrationRepository upstreamProviders(Providers config) {
    if (config.providers() == null || config.providers().isEmpty())
      throw new IllegalArgumentException("Enable federation only with configured providers");
    var registrations =
        config.providers().entrySet().stream()
            .map(
                entry -> {
                  var p = entry.getValue();
                  return ClientRegistration.withRegistrationId(entry.getKey())
                      .clientName(p.name())
                      .clientId(p.clientId())
                      .clientSecret(p.clientSecret())
                      .clientAuthenticationMethod(
                          new ClientAuthenticationMethod(
                              p.clientAuthenticationMethod() == null
                                  ? "client_secret_basic"
                                  : p.clientAuthenticationMethod()))
                      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                      .scope("openid", "profile")
                      .redirectUri(p.callback())
                      .issuerUri(p.issuer())
                      .authorizationUri(p.authorizationUri())
                      .tokenUri(p.tokenUri())
                      .jwkSetUri(p.jwksUri())
                      .userInfoUri(p.userInfoUri())
                      .userNameAttributeName("sub")
                      .build();
                })
            .toList();
    return new InMemoryClientRegistrationRepository(registrations);
  }
}
