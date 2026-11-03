package com.aobp.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the gateway boundary; shared infrastructure is discovered under com.aobp. */
@SpringBootApplication(scanBasePackages = "com.aobp")
public class GatewayApplication {
  /** Starts this independently deployable service. */
  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}
