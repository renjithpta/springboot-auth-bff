package com.aobp.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the bff boundary; shared infrastructure is discovered under com.aobp. */
@SpringBootApplication(scanBasePackages = "com.aobp")
public class BffApplication {
  /** Starts this independently deployable service. */
  public static void main(String[] args) {
    SpringApplication.run(BffApplication.class, args);
  }
}
