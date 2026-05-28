package com.luckledger.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The LuckLedger Spring Boot entry point. Scans the {@code com.luckledger.api} package for the
 * controllers, configuration, and the {@code GameSeeder} that boots the in-memory games at startup.
 * The whole engine is in-memory — there is no datastore to configure.
 */
@SpringBootApplication(scanBasePackages = "com.luckledger.api")
public class LuckLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuckLedgerApplication.class, args);
    }
}
