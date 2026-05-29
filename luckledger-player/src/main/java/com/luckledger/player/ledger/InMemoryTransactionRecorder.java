package com.luckledger.player.ledger;

import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link TransactionRecorder}: a thread-safe, per-player append-only list. Used by the
 * pure-domain module tests and any deployment that does not need durable persistence.
 *
 * <p>{@link #record(Transaction)} only appends; reads always return defensive, unmodifiable copies,
 * so callers can never mutate stored history. Instances are thread-safe because the recorder is a
 * shared collaborator written to by the bank, purchase, and reveal flows.
 */
public class InMemoryTransactionRecorder implements TransactionRecorder {

    private final Map<UUID, List<Transaction>> transactionsByPlayer = new ConcurrentHashMap<>();

    @Override
    public void record(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        transactionsByPlayer
                .computeIfAbsent(transaction.playerId(), id -> Collections.synchronizedList(new ArrayList<>()))
                .add(transaction);
    }

    @Override
    public List<Transaction> getTransactions(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return snapshot(playerId);
    }

    @Override
    public List<Transaction> getTransactions(UUID playerId, TransactionType type) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        return snapshot(playerId).stream()
                .filter(tx -> tx.type() == type)
                .toList();
    }

    @Override
    public List<Transaction> getRecentTransactions(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0 but was " + limit);
        }
        List<Transaction> all = snapshot(playerId);
        int count = Math.min(limit, all.size());
        List<Transaction> recent = new ArrayList<>(count);
        for (int i = all.size() - 1; i >= all.size() - count; i--) {
            recent.add(all.get(i));
        }
        return Collections.unmodifiableList(recent);
    }

    private List<Transaction> snapshot(UUID playerId) {
        List<Transaction> stored = transactionsByPlayer.get(playerId);
        if (stored == null) {
            return List.of();
        }
        synchronized (stored) {
            return List.copyOf(stored);
        }
    }
}
