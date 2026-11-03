package com.aobp.authserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adds one explicit fixture identity link only when the local simulator is requested. */
@Configuration
@ConditionalOnProperty(name = "app.simulator-link", havingValue = "true")
public class SimulatorLink {
  /** Links the simulator subject to the operator fixture; never matches email addresses. */
  @Bean
  @DependsOn("identitySchema")
  String simulatorIdentity(
      JdbcTemplate jdbc, @Value("${SIMULATOR_ISSUER:http://127.0.0.1:9001}") String issuer) {
    if (jdbc.queryForObject(
            "select count(*) from identity_link where issuer=? and subject_id=?",
            Integer.class,
            issuer,
            "sim-user")
        == 0)
      jdbc.update("insert into identity_link values(?,?,?)", issuer, "sim-user", "operator");
    return "linked";
  }
}
