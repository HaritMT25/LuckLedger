package com.luckledger.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.domain.ledger.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
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
 * Verifies the persistence layer against a real Postgres (Testcontainers): Flyway applies the V001
 * schema, Hibernate validates the entity mappings against it ({@code ddl-auto=validate}), and the
 * repositories round-trip players and ledger transactions with money precision intact.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LedgerPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private PlayerRepository players;

    @Autowired
    private TransactionRepository transactions;

    private PlayerEntity newPlayer(String name) {
        UUID id = UUID.randomUUID();
        return new PlayerEntity(
                id, name, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, Instant.now());
    }

    @Test
    void roundTripsAPlayerPreservingMoneyPrecision() {
        PlayerEntity player = newPlayer("Alice");
        player.setCoinBalance(new BigDecimal("100.5000"));
        player.setTotalBorrowed(new BigDecimal("100.5000"));
        players.saveAndFlush(player);

        PlayerEntity loaded = players.findById(player.getId()).orElseThrow();
        assertThat(loaded.getDisplayName()).isEqualTo("Alice");
        assertThat(loaded.getCoinBalance()).isEqualByComparingTo("100.5");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void appendsAndFiltersLedgerTransactions() {
        PlayerEntity player = players.saveAndFlush(newPlayer("Bob"));
        UUID pid = player.getId();

        transactions.save(txn(pid, TransactionType.BORROW, "100"));
        transactions.save(txn(pid, TransactionType.SPEND, "5"));
        transactions.save(txn(pid, TransactionType.WIN, "25"));
        transactions.flush();

        assertThat(transactions.findByPlayerIdOrderByCreatedAtAsc(pid)).hasSize(3);
        assertThat(transactions.findByPlayerIdAndTypeOrderByCreatedAtAsc(pid, TransactionType.SPEND))
                .singleElement()
                .satisfies(t -> assertThat(t.getAmount()).isEqualByComparingTo("5"));
    }

    private static TransactionEntity txn(UUID playerId, TransactionType type, String amount) {
        return new TransactionEntity(
                UUID.randomUUID(), playerId, type, new BigDecimal(amount), null, null, null, Instant.now());
    }
}
