package com.aobp.bff;

import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** Exposes a small session contract and explicit business endpoints to the UI. */
@RestController
public class BffController {
  private final FlightRelay relay;
  private final EncryptedClients clients;

  public BffController(FlightRelay relay, EncryptedClients clients) {
    this.relay = relay;
    this.clients = clients;
  }

  /** Returns identity presentation only, never OAuth tokens. */
  @GetMapping("/bff/api/v1/session")
  Map<String, Object> session(Authentication user, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    boolean signedIn =
        user != null
            && !(user
                instanceof
                org.springframework.security.authentication.AnonymousAuthenticationToken);
    return Map.of("authenticated", signedIn, "subject", signedIn ? user.getName() : "");
  }

  /** Materializes a session-bound CSRF token for state-changing browser requests. */
  @GetMapping("/bff/api/v1/csrf-token")
  Map<String, String> csrf(CsrfToken token, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
  }

  /** Invalidates this browser's session and deletes its authorized client. */
  @DeleteMapping("/bff/api/v1/session")
  ResponseEntity<Void> logout(
      Authentication user, HttpServletRequest request, HttpServletResponse response) {
    clients.removeAuthorizedClient("platform", user.getName());
    request.getSession().invalidate();
    SecurityContextHolder.clearContext();
    response.addHeader(
        "Set-Cookie",
        ResponseCookie.from("BFF_SESSION", "")
            .path("/")
            .httpOnly(true)
            .secure(request.isSecure())
            .sameSite("Lax")
            .maxAge(0)
            .build()
            .toString());
    return ResponseEntity.noContent().build();
  }

  /** Lists flights through a fixed downstream route. */
  @GetMapping("/bff/api/v1/flights")
  Object flights(Authentication user, HttpServletRequest request) {
    return relay.request(
        user, HttpMethod.GET, "/api/v1/flights", null, (String) request.getAttribute("requestId"));
  }

  /** Lets the owning service decide role and resource authorization for an update. */
  @PostMapping("/bff/api/v1/flights/{id}/delay-approvals")
  Object approve(@PathVariable String id, Authentication user, HttpServletRequest request) {
    if (!id.matches("[A-Z0-9-]{1,30}"))
      throw new org.springframework.web.server.ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid flight identifier");
    return relay.request(
        user,
        HttpMethod.POST,
        "/api/v1/flights/" + id + "/delay-approvals",
        Map.of(),
        (String) request.getAttribute("requestId"));
  }
}
