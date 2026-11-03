package com.aobp.flightservice;

import java.util.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Demonstrates service-owned RBAC and ABAC; the resource terminal comes from server data. */
@Component
public class FlightPolicy {
  /** Requires both a role and a token scope; a requested OAuth scope alone is insufficient. */
  public boolean canRead(Jwt jwt) {
    return scope(jwt, "flights.read")
        && (has(jwt, "roles", "VIEWER") || has(jwt, "roles", "OPERATOR"));
  }

  /** Applies RBAC, OAuth scope, and terminal ABAC together. */
  public boolean canApprove(Jwt jwt, String resourceTerminal) {
    return scope(jwt, "flights.write")
        && has(jwt, "roles", "OPERATOR")
        && terminal(jwt, resourceTerminal);
  }

  /** Limits visibility to assigned terminals, using an empty assignment as deny-all. */
  public boolean terminal(Jwt jwt, String terminal) {
    return has(jwt, "terminals", terminal);
  }

  private boolean scope(Jwt jwt, String scope) {
    Object value = jwt.getClaims().get("scope");
    if (value instanceof Collection<?> values) return values.contains(scope);
    return value instanceof String text && Arrays.asList(text.split(" ")).contains(scope);
  }

  private boolean has(Jwt jwt, String name, String value) {
    List<String> items = jwt.getClaimAsStringList(name);
    return items != null && items.contains(value);
  }
}
