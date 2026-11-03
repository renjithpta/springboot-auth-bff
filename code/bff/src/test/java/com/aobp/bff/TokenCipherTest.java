package com.aobp.bff;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Proves encryption is authenticated and different writes do not reuse a nonce. */
class TokenCipherTest {
  @Test
  void roundTripAndTamperDetection() {
    var cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    byte[] plain = "sensitive-refresh-token".getBytes();
    byte[] first = cipher.encrypt(plain), second = cipher.encrypt(plain);
    assertArrayEquals(plain, cipher.decrypt(first));
    assertFalse(Arrays.equals(first, second));
    first[first.length - 1] ^= 1;
    assertThrows(IllegalStateException.class, () -> cipher.decrypt(first));
  }

  @Test
  void rejectsInvalidKey() {
    assertThrows(IllegalArgumentException.class, () -> new TokenCipher("YQ=="));
  }

  /** Spring Session writes empty bytes for removed attributes; these must deserialize as null. */
  @Test
  void sessionSerializerHandlesRemovedAttributes() {
    var cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    var serializer = new BffSecurity().sessionSerializer(cipher);
    assertNull(serializer.deserialize(new byte[0]));
    assertNull(serializer.deserialize(null));
    assertEquals("session-value", serializer.deserialize(serializer.serialize("session-value")));
  }
}
