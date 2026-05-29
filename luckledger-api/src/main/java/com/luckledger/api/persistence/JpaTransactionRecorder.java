package com.luckledger.api.persistence;

import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.player.ledger.TransactionRecorder;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Postgres-backed {@link TransactionRecorder}: the append-only ledger persisted via
 * {@link TransactionRepository}. Replaces the in-memory recorder so the bank, purchase, and reveal
 * flows write durable transactions. Reads return ordered, unmodifiable lists of domain
 * {@link Transaction}s, mirroring the in-memory contract.
 *
 * <p>Wired as a {@code @Bean} in {@code ApiConfig} (not component-scanned) so the {@code @Transactional}
 * proxy is applied and the wiring works wherever {@code ApiConfig} is imported.
 */
public class JpaTransactionRecorder implements TransactionRecorder {

    private final TransactionRepository repository;

    public JpaTransactionRecorder(TransactionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    @Transactional
    public void record(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        repository.save(TransactionMapper.toEntity(transaction));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getTransactions(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return repository.findByPlayerIdOrderByCreatedAtAsc(playerId).stream()
                .map(TransactionMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getTransactions(UUID playerId, TransactionType type) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        return repository.findByPlayerIdAndTypeOrderByCreatedAtAsc(playerId, type).stream()
                .map(TransactionMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getRecentTransactions(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0 but was " + limit);
        }
        return repository.findByPlayerIdOrderByCreatedAtDesc(playerId, PageRequest.of(0, limit)).stream()
                .map(TransactionMapper::toDomain)
                .toList();
    }
}
