package com.luckledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The {@link GameSeeder} is an {@code ApplicationRunner}: it plants the two demo games at startup, and
 * must be idempotent so a restart against a populated database never duplicates the demo set. This runs
 * the seeder a second time and asserts the game/book/ticket counts are unchanged.
 */
@SpringBootTest(classes = {
        TestApplication.class,
        ApiConfig.class,
        GameSeeder.class
})
@Testcontainers
class GameSeederIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired private GameSeeder seeder;
    @Autowired private GameRepository games;
    @Autowired private TicketBookRepository books;
    @Autowired private TicketRepository tickets;

    @Test
    void reSeedingAnAlreadySeededDatabaseChangesNothing() {
        // The runner already fired once at startup: both demo games are present.
        long gameCount = games.count();
        long bookCount = books.count();
        long ticketCount = tickets.count();
        assertThat(gameCount).isEqualTo(2L);
        assertThat(bookCount).isGreaterThan(0L);
        assertThat(ticketCount).isGreaterThan(0L);

        seeder.run(null); // second run must short-circuit on the non-empty games table

        assertThat(games.count()).isEqualTo(gameCount);
        assertThat(books.count()).isEqualTo(bookCount);
        assertThat(tickets.count()).isEqualTo(ticketCount);
    }
}
