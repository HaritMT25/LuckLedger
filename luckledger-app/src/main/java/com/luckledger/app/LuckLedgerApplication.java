package com.luckledger.app;

import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.GameRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The LuckLedger Spring Boot entry point. Scans the {@code com.luckledger.api} package for the
 * controllers, configuration, and the {@code GameSeeder}; registers the JPA entities and Spring Data
 * repositories from {@code com.luckledger.api.persistence} (which lies outside this app package, so
 * it is enabled explicitly). Game/player/ledger state is persisted to Postgres via Flyway-managed
 * schema.
 */
@SpringBootApplication(scanBasePackages = "com.luckledger.api")
@EntityScan(basePackageClasses = GameEntity.class)
@EnableJpaRepositories(basePackageClasses = GameRepository.class)
public class LuckLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuckLedgerApplication.class, args);
    }
}
