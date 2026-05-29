package com.luckledger.player.ledger;

import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import java.util.List;
import java.util.UUID;

/**
 * Append-only store for player ledger transactions — the persistence boundary for Subsystem 11.
 *
 * <p>{@link #record(Transaction)} only ever appends; there is no update or delete, which upholds the
 * ledger's append-only invariant. Reads return chronologically ordered, unmodifiable results so
 * callers can never mutate stored history. Implementations are shared across the bank, purchase, and
 * reveal flows and must be safe for that use.
 *
 * @see InMemoryTransactionRecorder the default in-memory implementation
 */
public interface TransactionRecorder {

    /**
     * Appends a transaction to its player's ledger.
     *
     * @param transaction the transaction to record; never {@code null}
     * @throws NullPointerException if {@code transaction} is {@code null}
     */
    void record(Transaction transaction);

    /**
     * Returns all transactions for a player, oldest first.
     *
     * @param playerId the player whose ledger is read; never {@code null}
     * @return an unmodifiable, chronologically ordered copy; empty if the player has none
     * @throws NullPointerException if {@code playerId} is {@code null}
     */
    List<Transaction> getTransactions(UUID playerId);

    /**
     * Returns a player's transactions of a single type, oldest first.
     *
     * @param playerId the player whose ledger is read; never {@code null}
     * @param type     the transaction type to filter by; never {@code null}
     * @return an unmodifiable, chronologically ordered copy of the matching transactions; empty if
     *     none match
     * @throws NullPointerException if {@code playerId} or {@code type} is {@code null}
     */
    List<Transaction> getTransactions(UUID playerId, TransactionType type);

    /**
     * Returns a player's most recent transactions, newest first, capped at {@code limit}.
     *
     * @param playerId the player whose ledger is read; never {@code null}
     * @param limit    the maximum number of transactions to return; must be {@code > 0}
     * @return an unmodifiable list of up to {@code limit} transactions ordered newest first; empty if
     *     the player has none
     * @throws NullPointerException     if {@code playerId} is {@code null}
     * @throws IllegalArgumentException if {@code limit} is not strictly positive
     */
    List<Transaction> getRecentTransactions(UUID playerId, int limit);
}
