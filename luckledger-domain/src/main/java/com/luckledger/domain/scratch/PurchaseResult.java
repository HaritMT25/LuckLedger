package com.luckledger.domain.scratch;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of buying a ticket (the purchase half of the flow, before any reveal): which ticket
 * was sold, from which dealer and book, how many coins were debited, and the resulting status.
 *
 * <p>The {@code gridCommitment} is the ticket's public commit-reveal proof (SHA-256 of its grid),
 * shown to the buyer at purchase time so they can see the outcome was fixed before they bought — the
 * secret salt that would let it be verified is <em>not</em> exposed here (only on reveal). It is
 * {@code null} for legacy tickets generated before the commit-reveal scheme.
 *
 * @param ticketId the purchased ticket; never {@code null}
 * @param ticketStatus the ticket's status after purchase (normally {@link TicketStatus#SOLD}); never
 *     {@code null}
 * @param coinsDeducted the price debited from the player; never {@code null}, strictly positive
 * @param dealerId the dealer the ticket was bought from; never {@code null}
 * @param bookId the book the ticket came from; never {@code null}
 * @param gridCommitment the ticket's public commitment hash, or {@code null} for a legacy ticket
 */
public record PurchaseResult(
        UUID ticketId, TicketStatus ticketStatus, BigDecimal coinsDeducted, UUID dealerId, UUID bookId,
        String gridCommitment) {

    public PurchaseResult {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(ticketStatus, "ticketStatus must not be null");
        Objects.requireNonNull(coinsDeducted, "coinsDeducted must not be null");
        Objects.requireNonNull(dealerId, "dealerId must not be null");
        Objects.requireNonNull(bookId, "bookId must not be null");
        if (coinsDeducted.signum() <= 0) {
            throw new IllegalArgumentException(
                    "coinsDeducted must be > 0, was " + coinsDeducted.toPlainString());
        }
    }
}
