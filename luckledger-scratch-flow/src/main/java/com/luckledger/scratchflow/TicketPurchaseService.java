package com.luckledger.scratchflow;

import com.luckledger.distribution.BookDepletedException;
import com.luckledger.distribution.Dealer;
import com.luckledger.distribution.TicketBook;
import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.domain.player.Player;
import com.luckledger.domain.scratch.PurchaseResult;
import com.luckledger.domain.scratch.TicketStatus;
import com.luckledger.player.ledger.TransactionRecorder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The purchase half of the scratch flow (kept separate from reveal, §3.x): a player buys the next
 * ticket from a dealer's book. Coins are debited, the next ticket is drawn, and a {@code SPEND} is
 * appended to the ledger. The ticket is <em>not</em> revealed here — that is the
 * {@link ScratchRevealService}'s job, after the anticipation gap.
 *
 * <p>Only a {@link TransactionRecorder} is injected: affordability and the debit are the player's own
 * responsibility ({@link Player#debit}), and borrowing (the bank's role) is a separate player action,
 * so {@code BankService} plays no part in a purchase.
 *
 * <p>The ticket price is supplied by the caller — it lives on the pool contract, which a
 * {@link TicketBook} does not reference.
 */
public final class TicketPurchaseService {

    private final TransactionRecorder transactionRecorder;

    public TicketPurchaseService(TransactionRecorder transactionRecorder) {
        this.transactionRecorder =
                Objects.requireNonNull(transactionRecorder, "transactionRecorder must not be null");
    }

    /**
     * Buys the next ticket from {@code book}.
     *
     * @param player the buyer; never {@code null}
     * @param dealer the dealer selling the book; never {@code null}
     * @param book the book to draw from; never {@code null}
     * @param ticketPrice the price to debit; never {@code null}, strictly positive
     * @return the purchase outcome with the sold ticket
     * @throws BookDepletedException if the book has no tickets left (checked before debiting)
     * @throws com.luckledger.domain.player.InsufficientBalanceException if the player can't afford it
     */
    public PurchaseResult purchase(Player player, Dealer dealer, TicketBook book, BigDecimal ticketPrice) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(dealer, "dealer must not be null");
        Objects.requireNonNull(book, "book must not be null");
        Objects.requireNonNull(ticketPrice, "ticketPrice must not be null");
        if (ticketPrice.signum() <= 0) {
            throw new IllegalArgumentException("ticketPrice must be > 0, was " + ticketPrice.toPlainString());
        }
        if (book.isDepleted()) {
            throw new BookDepletedException("book " + book.bookId() + " is depleted");
        }

        // Debit first (throws before a ticket is drawn if the player can't afford it).
        player.debit(ticketPrice);
        TicketCard ticket = book.getNextTicket();
        if (book.isDepleted()) {
            dealer.onBookDepleted(book); // advances the dealer's lifetime count, driving re-ranking
        }

        Transaction spend = new Transaction(
                UUID.randomUUID(),
                player.getPlayerId(),
                TransactionType.SPEND,
                ticketPrice,
                dealer.dealerId(),
                book.bookId(),
                ticket.ticketId(),
                Instant.now());
        transactionRecorder.record(spend);

        // The in-memory scratch-flow predates persistence-side commit-reveal; no commitment is stamped
        // on these tickets, so it is null here. The persisted purchase path supplies the real hash.
        return new PurchaseResult(
                ticket.ticketId(), TicketStatus.SOLD, ticketPrice, dealer.dealerId(), book.bookId(), null);
    }
}
