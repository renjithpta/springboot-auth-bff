package com.aobp.bff;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Encrypts Redis records with authenticated AES-256-GCM and a fresh nonce per write. */
@Component
public class TokenCipher {
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public TokenCipher(@Value("${app.encryption-key}") String encoded) {
    byte[] bytes = Base64.getDecoder().decode(encoded);
    if (bytes.length != 32)
      throw new IllegalArgumentException("TOKEN_ENCRYPTION_KEY must be 32 bytes in base64");
    key = new SecretKeySpec(bytes, "AES");
  }

  /** Returns nonce followed by ciphertext and authentication tag. */
  public byte[] encrypt(byte[] value) {
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      var c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      byte[] body = c.doFinal(value);
      return ByteBuffer.allocate(12 + body.length).put(nonce).put(body).array();
    } catch (Exception e) {
      throw new IllegalStateException("Record encryption failed", e);
    }
  }

  /** Rejects altered records instead of returning unauthenticated plaintext. */
  public byte[] decrypt(byte[] value) {
    try {
      if (value.length < 28) throw new IllegalArgumentException("Invalid ciphertext");
      var c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, value, 0, 12));
      return c.doFinal(value, 12, value.length - 12);
    } catch (Exception e) {
      throw new IllegalStateException("Record authentication failed", e);
    }
  }
}
