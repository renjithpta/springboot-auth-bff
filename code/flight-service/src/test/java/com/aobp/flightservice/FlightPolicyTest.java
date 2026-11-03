package com.aobp.flightservice;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Covers role, OAuth scope, and authoritative resource scope independently. */
class FlightPolicyTest {
  private final FlightPolicy policy = new FlightPolicy();

  private Jwt token(String role, String scope, List<String> terminals) {
    return Jwt.withTokenValue("test")
        .header("alg", "RS256")
        .subject("operator")
        .claim("roles", List.of(role))
        .claim("scope", scope)
        .claim("terminals", terminals)
        .build();
  }

  @Test
  void operatorMayApproveAssignedTerminal() {
    assertTrue(policy.canApprove(token("OPERATOR", "flights.write", List.of("T1")), "T1"));
  }

  @Test
  void operatorCannotApproveAnotherTerminal() {
    assertFalse(policy.canApprove(token("OPERATOR", "flights.write", List.of("T1")), "T2"));
  }

  @Test
  void viewerCannotWriteEvenWithRequestedWriteScope() {
    assertFalse(policy.canApprove(token("VIEWER", "flights.write", List.of("T1")), "T1"));
  }

  @Test
  void roleCannotReplaceMissingScope() {
    assertFalse(policy.canApprove(token("OPERATOR", "flights.read", List.of("T1")), "T1"));
  }

  @Test
  void missingTerminalDenies() {
    assertFalse(policy.canApprove(token("OPERATOR", "flights.write", List.of()), "T1"));
  }
}
