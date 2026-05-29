package com.luckledger.api;

import com.luckledger.api.persistence.PlayerEntity;
import com.luckledger.api.persistence.PlayerRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot config root for the api module's slice tests (e.g. {@code @DataJpaTest}). The
 * real {@code @SpringBootApplication} lives in luckledger-app, which this module does not depend on,
 * so tests here need their own configuration anchor. Deliberately not a full {@code @SpringBootApplication}
 * (no component scan) so JPA slices don't drag in the controllers or in-memory services.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = PlayerEntity.class)
@EnableJpaRepositories(basePackageClasses = PlayerRepository.class)
public class TestApplication {}
