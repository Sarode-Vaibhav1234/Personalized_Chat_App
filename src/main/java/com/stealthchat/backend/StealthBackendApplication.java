package com.stealthchat.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StealthBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(StealthBackendApplication.class, args);
    }
}
