package com.aobp.authserver;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

/** Owns local credentials and explicit external-identity links; never links accounts by email. */
@Service
public class IdentityStore implements UserDetailsService {
  private final JdbcTemplate jdbc;

  public IdentityStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Loads an enabled local account for Spring's password authentication provider. */
  @Override
  public UserDetails loadUserByUsername(String username) {
    var rows = jdbc.queryForList("select * from app_user where username=?", username);
    if (rows.isEmpty()) throw new UsernameNotFoundException("Unknown account");
    var row = rows.get(0);
    return User.withUsername(username)
        .password((String) row.get("password_hash"))
        .disabled(!Boolean.TRUE.equals(row.get("enabled")))
        .authorities(authorities(username))
        .build();
  }

  /** Returns application roles, not arbitrary upstream provider authorities. */
  public List<SimpleGrantedAuthority> authorities(String username) {
    return jdbc.query(
        "select role_code from user_role where username=?",
        (r, n) -> new SimpleGrantedAuthority(r.getString(1)),
        username);
  }

  /** Returns resource scopes administered for this application account. */
  public List<String> terminals(String username) {
    return jdbc.query(
        "select terminal_code from user_terminal where username=?",
        (r, n) -> r.getString(1),
        username);
  }

  /**
   * Resolves an administrator-approved issuer/subject link and checks that the account remains
   * enabled.
   */
  public String externalUser(String issuer, String subject) {
    var users =
        jdbc.queryForList(
            "select u.username from identity_link l join app_user u on u.username=l.username where"
                + " l.issuer=? and l.subject_id=? and u.enabled=true",
            String.class,
            issuer,
            subject);
    if (users.size() != 1)
      throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
          "identity_not_onboarded");
    return users.get(0);
  }
}
