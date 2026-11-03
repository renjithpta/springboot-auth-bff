package com.aobp.bff;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;

/** Prevents losing the authorized client when Spring rotates the session ID after login. */
class ClientStorageKeyTest {
  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void storageKeySurvivesSessionFixationProtection() {
    var request = new MockHttpServletRequest();
    request.getSession();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    var clients = new EncryptedClients(null, null, null, null);
    String before = clients.key("platform", "operator");
    request.changeSessionId();
    assertEquals(before, clients.key("platform", "operator"));
  }

  @Test
  void sameUserInTwoBrowsersHasIndependentTokens() {
    var clients = new EncryptedClients(null, null, null, null);
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    String first = clients.key("platform", "operator");
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    assertNotEquals(first, clients.key("platform", "operator"));
  }
}
