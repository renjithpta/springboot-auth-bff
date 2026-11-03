package com.aobp.flightservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises HTTP security separately from policy unit tests. */
@WebMvcTest(FlightController.class)
@Import({ApiSecurity.class, FlightPolicy.class})
@org.springframework.test.context.TestPropertySource(
    properties = {
      "app.issuer=http://issuer",
      "app.jwks=http://issuer/oauth2/jwks",
      "app.audience=flight-api"
    })
class ApiBoundaryTest {
  @Autowired MockMvc mvc;

  @Test
  void anonymousCannotList() throws Exception {
    mvc.perform(get("/api/v1/flights")).andExpect(status().isUnauthorized());
  }

  @Test
  void viewerGetsOnlyAssignedTerminal() throws Exception {
    mvc.perform(
            get("/api/v1/flights")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.claim("roles", List.of("VIEWER"))
                                    .claim("scope", "flights.read")
                                    .claim("terminals", List.of("T1")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void terminalDenialReturnsProblem() throws Exception {
    mvc.perform(
            post("/api/v1/flights/FL-202/delay-approvals")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.claim("roles", List.of("OPERATOR"))
                                    .claim("scope", "flights.write")
                                    .claim("terminals", List.of("T1")))))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }
}
