package com.luckledger.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.api.GameStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the full application context — wiring every domain service into Spring beans and running the
 * {@code GameSeeder} — and confirms the two demo games (Celestial Fortune, Demon Seal) were seeded.
 * This is the end-to-end proof that the entire composition assembles and generates cleanly.
 */
@SpringBootTest
class LuckLedgerApplicationTest {

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
