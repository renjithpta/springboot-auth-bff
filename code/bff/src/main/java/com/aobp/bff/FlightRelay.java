package com.aobp.bff;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Relays only fixed flight routes; browser-supplied URLs or authorization headers are never
 * forwarded.
 */
@Service
public class FlightRelay {
  private final OAuth2AuthorizedClientManager manager;
  private final EncryptedClients clients;
  private final StringRedisTemplate redis;
  private final RestClient api;

  public FlightRelay(
      OAuth2AuthorizedClientManager manager,
      EncryptedClients clients,
      StringRedisTemplate redis,
      @Value("${app.flight-url}") String url) {
    this.manager = manager;
    this.clients = clients;
    this.redis = redis;
    var factory =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.api = RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }

  /** Coordinates refresh for one session across replicas, then releases only its own lock. */
  private String accessToken(Authentication user) {
    String lock = clients.key("platform", user.getName()) + ":lock",
        owner = UUID.randomUUID().toString();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    while (!Boolean.TRUE.equals(
        redis.opsForValue().setIfAbsent(lock, owner, Duration.ofSeconds(30)))) {
      if (System.nanoTime() > deadline)
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token refresh busy");
      try {
        Thread.sleep(40);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
      }
    }
    try {
      var client =
          manager.authorize(
              OAuth2AuthorizeRequest.withClientRegistrationId("platform").principal(user).build());
      if (client == null)
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign-in required");
      return client.getAccessToken().getTokenValue();
    } catch (OAuth2AuthorizationException e) {
      String code = e.getError().getErrorCode();
      org.slf4j.LoggerFactory.getLogger(getClass()).warn("Refresh failed code={} detail={}",code,e.getMessage());
      if ("invalid_grant".equals(code) || "invalid_token".equals(code)) {
        clients.removeAuthorizedClient("platform", user.getName());
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign-in required");
      }
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Identity service unavailable");
    } finally {
      redis.execute(
          new DefaultRedisScript<>(
              "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else"
                  + " return 0 end",
              Long.class),
          List.of(lock),
          owner);
    }
  }

  /** Calls a fixed resource path with a server-owned bearer token and correlation ID. */
  public Object request(
      Authentication user, HttpMethod method, String path, Object body, String requestId) {
    var call =
        api.method(method)
            .uri(path)
            .headers(
                h -> {
                  h.setBearerAuth(accessToken(user));
                  h.set("X-Request-ID", requestId);
                });
    if (body != null) call.contentType(MediaType.APPLICATION_JSON).body(body);
    // Never replay writes automatically: a timeout may occur after the operation committed.
    return call.retrieve().body(Object.class);
  }
}
