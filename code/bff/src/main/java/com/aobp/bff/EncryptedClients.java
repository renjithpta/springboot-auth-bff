package com.aobp.bff;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.stereotype.Service;

/**
 * Stores each browser session's authorized client separately; different logins never share refresh
 * tokens.
 */
@Service
public class EncryptedClients implements OAuth2AuthorizedClientService {
  private final StringRedisTemplate redis;
  private final TokenCipher cipher;
  private final ObjectMapper json;
  private final ClientRegistrationRepository registrations;

  public EncryptedClients(
      StringRedisTemplate redis,
      TokenCipher cipher,
      ObjectMapper json,
      ClientRegistrationRepository registrations) {
    this.redis = redis;
    this.cipher = cipher;
    this.json = json;
    this.registrations = registrations;
  }

  /** Uses a session-owned storage ID that survives Spring's login-time session ID rotation. */
  public String key(String registration, String principal) {
    var attrs =
        (org.springframework.web.context.request.ServletRequestAttributes)
            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
    var httpSession = attrs.getRequest().getSession();
    String session = (String) httpSession.getAttribute("AUTHORIZED_CLIENT_STORAGE_ID");
    if (session == null) {
      session = UUID.randomUUID().toString();
      httpSession.setAttribute("AUTHORIZED_CLIENT_STORAGE_ID", session);
    }
    try {
      return "aobp:client:"
          + HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          (registration + ":" + principal + ":" + session)
                              .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Reconstructs OAuth objects only after authenticated decryption. */
  @SuppressWarnings("unchecked")
  @Override
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, String principal) {
    String encoded = redis.opsForValue().get(key(id, principal));
    if (encoded == null) return null;
    try {
      var d = json.readValue(cipher.decrypt(Base64.getDecoder().decode(encoded)), Record.class);
      var access =
          new OAuth2AccessToken(
              OAuth2AccessToken.TokenType.BEARER,
              d.access,
              Instant.parse(d.issued),
              Instant.parse(d.expires),
              new HashSet<>(d.scopes));
      var refresh =
          d.refresh == null
              ? null
              : new OAuth2RefreshToken(d.refresh, Instant.parse(d.refreshIssued));
      return (T)
          new OAuth2AuthorizedClient(
              registrations.findByRegistrationId(id), principal, access, refresh);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot read authorized client", e);
    }
  }

  /** Applies the BFF session's maximum lifetime to token records. */
  @Override
  public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal) {
    try {
      var a = client.getAccessToken();
      var f = client.getRefreshToken();
      var d =
          new Record(
              a.getTokenValue(),
              a.getIssuedAt().toString(),
              a.getExpiresAt().toString(),
              a.getScopes(),
              f == null ? null : f.getTokenValue(),
              f == null ? null : f.getIssuedAt().toString());
      redis
          .opsForValue()
          .set(
              key(client.getClientRegistration().getRegistrationId(), principal.getName()),
              Base64.getEncoder().encodeToString(cipher.encrypt(json.writeValueAsBytes(d))),
              Duration.ofMinutes(30));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot save authorized client", e);
    }
  }

  /** Removes the tokens belonging to this browser session. */
  @Override
  public void removeAuthorizedClient(String id, String principal) {
    redis.delete(key(id, principal));
  }

  private record Record(
      String access,
      String issued,
      String expires,
      Set<String> scopes,
      String refresh,
      String refreshIssued) {}
}
