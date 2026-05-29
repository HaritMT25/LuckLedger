package com.luckledger.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the Postgres-backed {@link JpaTransactionRecorder} against a real database: records map to
 * rows and back to domain {@link Transaction}s with money precision intact, type filtering works, and
 * recent-first ordering is honoured.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class JpaTransactionRecorderTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private PlayerRepository players;

    @Test
    void recordsAndReadsBackDomainTransactions() {
        JpaTransactionRecorder recorder = new JpaTransactionRecorder(transactions);
        UUID playerId = seedPlayer();
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        recorder.record(txn(playerId, TransactionType.BORROW, "100.5000", base));
        recorder.record(txn(playerId, TransactionType.SPEND, "5", base.plusSeconds(1)));
        recorder.record(txn(playerId, TransactionType.WIN, "25", base.plusSeconds(2)));

        assertThat(recorder.getTransactions(playerId)).hasSize(3);
        assertThat(recorder.getTransactions(playerId, TransactionType.BORROW))
                .singleElement()
                .satisfies(t -> assertThat(t.amount()).isEqualByComparingTo("100.5"));

        // Newest first, capped at the limit.
        assertThat(recorder.getRecentTransactions(playerId, 2))
                .extracting(Transaction::type)
                .containsExactly(TransactionType.WIN, TransactionType.SPEND);
    }

    private UUID seedPlayer() {
        UUID id = UUID.randomUUID();
        players.saveAndFlush(new PlayerEntity(
                id, "Ledger", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, Instant.now()));
        return id;
    }

    private static Transaction txn(UUID playerId, TransactionType type, String amount, Instant at) {
        return new Transaction(UUID.randomUUID(), playerId, type, new BigDecimal(amount), null, null, null, at);
    }
}
