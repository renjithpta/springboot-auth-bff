package com.aobp.authserver;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Initializes the sample database; production migrations should be managed separately. */
@Configuration
public class DatabaseSetup {
  /**
   * Creates OAuth protocol tables and application identity tables before repositories are
   * constructed.
   */
  @Bean
  String identitySchema(
      JdbcTemplate jdbc,
      @Value("${app.demo-users}") boolean demo,
      @Value("${app.demo-password}") String password,
      PasswordEncoder encoder)
      throws Exception {
    for (String table :
        new String[] {
          "oauth2_registered_client", "oauth2_authorization", "oauth2_authorization_consent"
        }) {
      String path =
          "org/springframework/security/oauth2/server/authorization/"
              + (table.equals("oauth2_registered_client") ? "client/" : "")
              + table.replace('_', '-')
              + "-schema.sql";
      String sql =
          new ClassPathResource(path)
              .getContentAsString(StandardCharsets.UTF_8)
              .replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ")
              .replace("blob", "text");
      for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    }
    jdbc.execute(
        "create table if not exists app_user(username varchar(100) primary key,password_hash"
            + " varchar(200) not null,enabled boolean not null)");
    jdbc.execute(
        "create table if not exists user_role(username varchar(100) references"
            + " app_user(username),role_code varchar(80),primary key(username,role_code))");
    jdbc.execute(
        "create table if not exists user_terminal(username varchar(100) references"
            + " app_user(username),terminal_code varchar(80),primary key(username,terminal_code))");
    jdbc.execute(
        "create table if not exists identity_link(issuer varchar(400),subject_id"
            + " varchar(200),username varchar(100) references app_user(username),primary"
            + " key(issuer,subject_id))");
    if (demo) {
      if (password.length() < 16)
        throw new IllegalArgumentException("DEMO_PASSWORD must have at least 16 characters");
      for (String user : new String[] {"operator", "viewer", "sim-user"})
        if (jdbc.queryForObject(
                "select count(*) from app_user where username=?", Integer.class, user)
            == 0) {
          jdbc.update("insert into app_user values(?,?,true)", user, encoder.encode(password));
          jdbc.update(
              "insert into user_role values(?,?)",
              user,
              user.equals("viewer") ? "VIEWER" : "OPERATOR");
          jdbc.update("insert into user_terminal values(?,?)", user, "T1");
        }
    }
    return "ready";
  }
}
