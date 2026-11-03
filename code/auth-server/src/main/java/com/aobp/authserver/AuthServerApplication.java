package com.aobp.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the auth-server boundary; shared infrastructure is discovered under com.aobp. */
@SpringBootApplication(scanBasePackages = "com.aobp")
public class AuthServerApplication {
  /** Starts this independently deployable service. */
  public static void main(String[] args) {
    SpringApplication.run(AuthServerApplication.class, args);
  }
}
