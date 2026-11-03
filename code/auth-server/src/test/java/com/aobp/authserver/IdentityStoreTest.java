package com.aobp.authserver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/** Prevents unapproved external identities from receiving application access. */
class IdentityStoreTest {
  @Test
  void rejectsUnlinkedIdentity() {
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(anyString(), eq(String.class), eq("issuer"), eq("subject")))
        .thenReturn(List.of());
    assertThrows(
        OAuth2AuthenticationException.class,
        () -> new IdentityStore(jdbc).externalUser("issuer", "subject"));
  }
}
