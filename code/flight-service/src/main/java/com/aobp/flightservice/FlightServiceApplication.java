package com.aobp.flightservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the flight-service boundary; shared infrastructure is discovered under com.aobp. */
@SpringBootApplication(scanBasePackages = "com.aobp")
public class FlightServiceApplication {
  /** Starts this independently deployable service. */
  public static void main(String[] args) {
    SpringApplication.run(FlightServiceApplication.class, args);
  }
}
