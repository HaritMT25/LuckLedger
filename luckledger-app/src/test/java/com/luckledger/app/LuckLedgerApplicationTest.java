package com.luckledger.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.api.GameStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application context — wiring every domain service into Spring beans, running Flyway
 * against a real Postgres, and running the {@code GameSeeder} — and confirms the two demo games
 * (Celestial Fortune, Demon Seal) were seeded. End-to-end proof the whole composition assembles.
 */
@SpringBootTest
@Testcontainers
class LuckLedgerApplicationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private GameStore gameStore;

    @Test
    void contextLoadsAndSeedsTheTwoDemoGames() {
        assertThat(gameStore.games()).hasSize(2);
        assertThat(gameStore.games())
                .allSatisfy(game ->
                        assertThat(game.setup().generationResult().verificationReport().passed()).isTrue());
    }
}
