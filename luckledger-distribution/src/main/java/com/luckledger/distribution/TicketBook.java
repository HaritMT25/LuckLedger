package com.luckledger.distribution;

import com.luckledger.domain.generation.TicketCard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * An ordered book of tickets, sold strictly sequentially (ticket #1 first, then #2, ...). A book is
 * the unit a dealer receives and depletes; it tracks how far through the sequence it has been sold
 * but never reorders or skips (§3.12).
 *
 * <p>The ticket list is fixed at construction; only the sale cursor advances. Book value and prizes
 * dispensed are derived from the tickets' predetermined prizes, never stored separately.
 */
public final class TicketBook {

    private final java.util.UUID bookId;
    private final List<TicketCard> tickets;
    private final java.util.UUID poolContractId;
    private int nextIndex;

    /**
     * @param bookId the book's id; never {@code null}
     * @param tickets the ordered tickets; never {@code null}, non-empty, no null elements (copied)
     * @param poolContractId the pool this book was partitioned from; never {@code null}
     */
    public TicketBook(java.util.UUID bookId, List<TicketCard> tickets, java.util.UUID poolContractId) {
        Objects.requireNonNull(bookId, "bookId must not be null");
        Objects.requireNonNull(tickets, "tickets must not be null");
        Objects.requireNonNull(poolContractId, "poolContractId must not be null");
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("tickets must not be empty");
        }
        tickets.forEach(t -> Objects.requireNonNull(t, "tickets must not contain null elements"));
        this.bookId = bookId;
        this.tickets = List.copyOf(tickets);
        this.poolContractId = poolContractId;
        this.nextIndex = 0;
    }

    public java.util.UUID bookId() {
        return bookId;
    }

    public java.util.UUID poolContractId() {
        return poolContractId;
    }

    public int getTotalTickets() {
        return tickets.size();
    }

    public int getTicketsRemaining() {
        return tickets.size() - nextIndex;
    }

    /** Total face value of the book: the sum of every ticket's predetermined prize. */
    public BigDecimal getBookValue() {
        return tickets.stream()
                .map(card -> card.layout().prizeAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Total prizes on tickets already sold (indices {@code 0..nextIndex-1}). */
    public BigDecimal getPrizesDispensed() {
        BigDecimal dispensed = BigDecimal.ZERO;
        for (int i = 0; i < nextIndex; i++) {
            dispensed = dispensed.add(tickets.get(i).layout().prizeAmount());
        }
        return dispensed;
    }

    /**
     * Hands out the next unsold ticket and advances the cursor.
     *
     * @return the next ticket in sequence
     * @throws BookDepletedException if every ticket has already been sold
     */
    public TicketCard getNextTicket() {
        if (isDepleted()) {
            throw new BookDepletedException("book " + bookId + " is depleted (" + tickets.size() + " sold)");
        }
        return tickets.get(nextIndex++);
    }

    public boolean isDepleted() {
        return nextIndex >= tickets.size();
    }
}
