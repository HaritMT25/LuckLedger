package com.luckledger.domain.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single immutable entry in a player's append-only ledger.
 *
 * <p>Every coin movement is recorded as one {@code Transaction}. The direction of the movement is
 * carried by {@link #type()} — {@code BORROW} and {@code WIN} credit the player, {@code SPEND}
 * debits them — so {@link #amount()} is always a positive magnitude.
 *
 * <p>{@code dealerId}, {@code bookId}, and {@code ticketId} are nullable: a {@code BORROW} from the
 * bank has no dealer, book, or ticket, whereas a {@code SPEND} or {@code WIN} references the ticket
 * the player bought or revealed.
 *
 * @param transactionId stable public identifier of this transaction; never {@code null}
 * @param playerId      identifier of the player the transaction belongs to; never {@code null}
 * @param type          the kind of coin movement; never {@code null}
 * @param amount        positive coin magnitude moved by this transaction; never {@code null}, {@code > 0}
 * @param dealerId      dealer involved, or {@code null} for transactions with no dealer (e.g. {@code BORROW})
 * @param bookId        book involved, or {@code null} for transactions with no book (e.g. {@code BORROW})
 * @param ticketId      ticket involved, or {@code null} for transactions with no ticket (e.g. {@code BORROW})
 * @param timestamp     instant the transaction occurred; never {@code null}
 */
public record Transaction(
        UUID transactionId,
        UUID playerId,
        TransactionType type,
        BigDecimal amount,
        UUID dealerId,
        UUID bookId,
        UUID ticketId,
        Instant timestamp) {

    /**
     * Validates the transaction on construction.
     *
     * @throws NullPointerException     if {@code transactionId}, {@code playerId}, {@code type},
     *                                  {@code amount}, or {@code timestamp} is {@code null}
     * @throws IllegalArgumentException if {@code amount} is not strictly positive
     */
    public Transaction {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be > 0 but was " + amount);
        }
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
