package com.luckledger.domain.ledger;

/**
 * The kind of movement recorded by an append-only ledger transaction.
 *
 * <ul>
 *   <li>{@link #BORROW} — the bank grants the player free virtual coins.</li>
 *   <li>{@link #SPEND} — the player buys a ticket, debiting their balance.</li>
 *   <li>{@link #WIN} — a revealed ticket is a winner, crediting their balance.</li>
 * </ul>
 */
public enum TransactionType {
    BORROW,
    SPEND,
    WIN
}
