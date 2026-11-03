package com.aobp.flightservice;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Uses deterministic sample resources so allow/deny behavior is easy to demonstrate. */
@RestController
public class FlightController {
  private final FlightPolicy policy;
  private final List<Flight> flights =
      List.of(
          new Flight("FL-101", "T1", "London", "Scheduled"),
          new Flight("FL-202", "T2", "Dubai", "Scheduled"));

  public FlightController(FlightPolicy policy) {
    this.policy = policy;
  }

  /** Filters resources after checking the caller's read permission. */
  @GetMapping("/api/v1/flights")
  Map<String, Object> list(@AuthenticationPrincipal Jwt jwt) {
    if (!policy.canRead(jwt))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing flight read permission");
    return Map.of(
        "data", flights.stream().filter(f -> policy.terminal(jwt, f.terminal())).toList());
  }

  /** Simulates an approval; no flight-state persistence is claimed by this security sample. */
  @PostMapping("/api/v1/flights/{id}/delay-approvals")
  Map<String, Object> approve(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
    Flight flight =
        flights.stream()
            .filter(f -> f.id().equals(id))
            .findFirst()
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
    if (!policy.canApprove(jwt, flight.terminal()))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Role or terminal policy denied approval");
    org.slf4j.LoggerFactory.getLogger(getClass())
        .info("business event=delay_approval_simulated flight={} subject={}", id, jwt.getSubject());
    return Map.of(
        "data",
        Map.of(
            "flightId", id, "result", "SIMULATED_APPROVAL", "timestamp", Instant.now().toString()));
  }

  public record Flight(String id, String terminal, String destination, String status) {}
}
