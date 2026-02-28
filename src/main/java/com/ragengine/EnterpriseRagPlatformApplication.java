package com.ragengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class EnterpriseRagPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseRagPlatformApplication.class, args);
    }
}
