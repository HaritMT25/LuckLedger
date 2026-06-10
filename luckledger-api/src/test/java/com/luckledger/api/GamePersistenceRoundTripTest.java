package com.luckledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.GamePersistenceMapper;
import com.luckledger.api.persistence.GamePersistenceMapper.PersistedGame;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.cli.GameOrchestrator;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.orchestration.GameConfig;
import com.luckledger.domain.scratch.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Persists a freshly generated game graph (game → dealers → books → tickets) to a real Postgres via
 * the {@link GamePersistenceMapper}, then reloads it to prove the whole graph round-trips: JSONB grids
 * deserialize back, money keeps its precision, and ticket order/positions survive. Uses the small
 * Demon Seal demo (20 tickets) so the test is fast.
 */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@Import(ApiConfig.class)
@Testcontainers
class GamePersistenceRoundTripTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    @Qualifier("demonOrchestrator")
    private GameOrchestrator demonOrchestrator;

    @Autowired
    private GameRepository games;

    @Autowired
    private DealerRepository dealers;

    @Autowired
    private TicketBookRepository books;

    @Autowired
    private TicketRepository tickets;

    @Test
    void persistsAndReloadsTheFullGameGraph() {
        GameConfig config = ApiConfig.demonConfig();
        GameSetupResult setup = demonOrchestrator.setup(config);
        UUID gameId = UUID.randomUUID();

        PersistedGame persisted = GamePersistenceMapper.toPersisted(gameId, config, setup, Instant.now());
        // Save in FK order: game, then shops (one row per allocation slot, id == the slot's dealer id),
        // then books, tickets.
        games.saveAndFlush(persisted.game());
        setup.dealers().forEach(d -> dealers.saveAndFlush(new DealerEntity(
                d.dealerId(), d.name(), "Owner", null, List.of(gameId),
                d.tier(), d.rankScore(), d.booksPerCycle(), d.booksDepleted())));
        books.saveAllAndFlush(persisted.books());
        tickets.saveAllAndFlush(persisted.tickets());

        int generatedTickets = setup.generationResult().tickets().size();

        // --- game row ---
        GameEntity game = games.findById(gameId).orElseThrow();
        assertThat(game.getMechanicType()).isEqualTo(MechanicType.DEMON_SEAL);
        assertThat(game.getTicketPrice()).isEqualByComparingTo("5");
        assertThat(game.getPayoutRatio()).isEqualByComparingTo("0.648");
        assertThat(game.isVerificationPassed()).isTrue();
        assertThat(game.getNearMiss()).isNotNull(); // JSONB report round-tripped
        assertThat(game.getTotalTickets()).isEqualTo(generatedTickets);

        // --- shops --- (one persisted row per allocation slot)
        assertThat(dealers.findAll()).hasSize(setup.dealers().size());

        // --- books + tickets ---
        List<TicketBookEntity> reloadedBooks = books.findByGameId(gameId);
        assertThat(reloadedBooks).hasSize(setup.partitionResult().books().size());
        assertThat(tickets.findByGameId(gameId)).hasSize(generatedTickets);

        TicketBookEntity book = reloadedBooks.get(0);
        List<TicketEntity> bookTickets = tickets.findByBookIdOrderByPositionInBookAsc(book.getId());
        assertThat(bookTickets).isNotEmpty();
        assertThat(bookTickets).hasSize(book.getTotalTickets());

        // Positions are dense and ascending from 0.
        for (int i = 0; i < bookTickets.size(); i++) {
            assertThat(bookTickets.get(i).getPositionInBook()).isEqualTo(i);
        }

        // A freshly seeded ticket: AVAILABLE, not revealed, with both JSONB grids intact.
        TicketEntity ticket = bookTickets.get(0);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.AVAILABLE);
        assertThat(ticket.isRevealed()).isFalse();
        assertThat(ticket.getRevealedIsWinner()).isNull();
        assertThat(ticket.getMechanicType()).isEqualTo(MechanicType.DEMON_SEAL);
        assertThat(ticket.getPrizeAmount()).isNotNull();
        assertThat(ticket.getGrid()).isNotNull();
        assertThat(ticket.getGrid().cells()).isNotEmpty();
        assertThat(ticket.getSkinnedGrid()).isNotNull();
        assertThat(ticket.getSkinnedGrid().cells()).isNotEmpty();
        assertThat(ticket.getGrid().dimension()).isEqualTo(ticket.getSkinnedGrid().dimension());
    }
}
