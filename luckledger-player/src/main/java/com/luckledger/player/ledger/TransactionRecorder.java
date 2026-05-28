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
 * Append-only store for player ledger transactions.
 *
 * <p>This is the persistence boundary for Subsystem 11. {@link #record(Transaction)} only ever
 * appends — there is no update or delete — which upholds the ledger's append-only invariant. Reads
 * always return defensive, unmodifiable copies, so callers can never mutate stored history.
 *
 * <p>Instances are thread-safe: the recorder is a shared collaborator written to by the bank,
 * purchase, and reveal flows, potentially from different threads.
 */
public class TransactionRecorder {

    private final Map<UUID, List<Transaction>> transactionsByPlayer = new ConcurrentHashMap<>();

    /**
     * Appends a transaction to its player's ledger.
     *
     * @param transaction the transaction to record; never {@code null}
     * @throws NullPointerException if {@code transaction} is {@code null}
     */
    public void record(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        transactionsByPlayer
                .computeIfAbsent(transaction.playerId(), id -> Collections.synchronizedList(new ArrayList<>()))
                .add(transaction);
    }

    /**
     * Returns all transactions for a player, oldest first.
     *
     * @param playerId the player whose ledger is read; never {@code null}
     * @return an unmodifiable, chronologically ordered copy of the player's transactions; empty if
     *     the player has none
     * @throws NullPointerException if {@code playerId} is {@code null}
     */
    public List<Transaction> getTransactions(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return snapshot(playerId);
    }

    /**
     * Returns a player's transactions of a single type, oldest first.
     *
     * @param playerId the player whose ledger is read; never {@code null}
     * @param type     the transaction type to filter by; never {@code null}
     * @return an unmodifiable, chronologically ordered copy of the matching transactions; empty if
     *     none match
     * @throws NullPointerException if {@code playerId} or {@code type} is {@code null}
     */
    public List<Transaction> getTransactions(UUID playerId, TransactionType type) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        return snapshot(playerId).stream()
                .filter(tx -> tx.type() == type)
                .toList();
    }

    /**
     * Returns a player's most recent transactions, newest first, capped at {@code limit}.
     *
     * @param playerId the player whose ledger is read; never {@code null}
     * @param limit    the maximum number of transactions to return; must be {@code > 0}
     * @return an unmodifiable list of up to {@code limit} transactions ordered newest first; empty
     *     if the player has none
     * @throws NullPointerException     if {@code playerId} is {@code null}
     * @throws IllegalArgumentException if {@code limit} is not strictly positive
     */
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
