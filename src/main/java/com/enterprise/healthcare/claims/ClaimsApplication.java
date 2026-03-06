package com.enterprise.healthcare.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Healthcare Claims Adjudication System
 *
 * Entry point for the Spring Boot application. This system provides
 * real-time claims adjudication for healthcare payers, supporting
 * HMO, PPO, EPO, and HDHP plan types.
 *
 * @version 1.0.0
 */
@SpringBootApplication
public class ClaimsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimsApplication.class, args);
    }
}
