package com.luckledger.api;

import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.PlayerEntity;
import com.luckledger.api.persistence.PlayerMapper;
import com.luckledger.api.persistence.PlayerRepository;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.distribution.BookDepletedException;
import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.domain.orchestration.GameStatus;
import com.luckledger.domain.player.Player;
import com.luckledger.domain.scratch.PurchaseResult;
import com.luckledger.domain.scratch.TicketStatus;
import com.luckledger.player.ledger.TransactionRecorder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The purchase half of the scratch flow, persisted: a player buys the next ticket from a dealer's
 * book. In one transaction it debits the player, draws the next sequential ticket (marking it SOLD and
 * advancing the book's sale cursor), advances the dealer's depletion count when the book runs out, and
 * appends a {@code SPEND} to the ledger.
 *
 * <p>The debit runs first (via the pure {@link Player#debit}, which throws if unaffordable) so an
 * unaffordable purchase changes nothing. The ticket is not revealed here — that is the
 * {@link RevealGateway}'s job.
 *
 * <p><strong>Lock order (writer rule): book/ticket first, player LAST.</strong> The book row is taken
 * under a pessimistic write lock at the very start, so the depleted-book check and the sale-cursor
 * advance run serialized — two concurrent buyers of the same book are ordered, never handed the same
 * slot. The drawn ticket row is then locked (with a sold-check) so the sale is also race-safe at the
 * row level, and the player row is locked last. Every writer (purchase, reveal, borrow) obtains locks
 * in this same order to make deadlock impossible. The dealer depletion counter is <em>not</em> part of
 * the lock set: it is bumped with a single atomic in-place increment ({@code booksDepleted + 1}) rather
 * than a read-modify-write, so it needs no lock and cannot lost-update across books of the same dealer.
 */
@Service
public class PurchaseGateway {

    private final PlayerRepository players;
    private final GameRepository games;
    private final TicketBookRepository books;
    private final TicketRepository tickets;
    private final DealerRepository dealers;
    private final TransactionRecorder recorder;

    public PurchaseGateway(PlayerRepository players, GameRepository games, TicketBookRepository books,
            TicketRepository tickets, DealerRepository dealers, TransactionRecorder recorder) {
        this.players = players;
        this.games = games;
        this.books = books;
        this.tickets = tickets;
        this.dealers = dealers;
        this.recorder = recorder;
    }

    /**
     * Buys the next ticket from the given book for the given player.
     *
     * @throws NoSuchElementException if the book or player does not exist, or the book is unallocated
     * @throws BookDepletedException if the book has no tickets left
     * @throws com.luckledger.domain.player.InsufficientBalanceException if the player can't afford it
     */
    @Transactional
    public PurchaseResult purchase(UUID bookId, UUID playerId) {
        Objects.requireNonNull(bookId, "bookId");
        Objects.requireNonNull(playerId, "playerId");

        // Lock the book FIRST: the depleted check and cursor advance must be serialized so two
        // concurrent buyers cannot draw the same slot.
        TicketBookEntity book = books.findByIdForUpdate(bookId)
                .orElseThrow(() -> new NoSuchElementException("no book with id " + bookId));
        UUID dealerId = book.getDealerId();
        if (dealerId == null) {
            throw new NoSuchElementException("book " + bookId + " is not allocated to a dealer");
        }
        if (book.getNextIndex() >= book.getTotalTickets()) {
            throw new BookDepletedException("book " + bookId + " is depleted");
        }

        GameEntity game = games.findById(book.getGameId())
                .orElseThrow(() -> new NoSuchElementException("no game with id " + book.getGameId()));
        // A retired campaign is off the shelf: no new purchases. Reveal of already-sold tickets is
        // untouched — this gate is only on the buy path. (409 CONFLICT via IllegalStateException.)
        if (game.getStatus() == GameStatus.RETIRED) {
            throw new IllegalStateException("game " + game.getId() + " is retired and not for sale");
        }
        BigDecimal price = game.getTicketPrice();

        // Lock the drawn ticket SECOND (before the player, mirroring RevealGateway's ticket-then-player
        // order) and assert it is unsold in the same transaction. This makes the sale race-safe at the
        // row level independent of the book lock: SELECT FOR UPDATE + sold-check + write cannot
        // interleave with another sale of this row.
        UUID drawnTicketId = tickets.findByBookIdAndPositionInBook(bookId, book.getNextIndex())
                .map(TicketEntity::getId)
                .orElseThrow(() -> new NoSuchElementException(
                        "book " + bookId + " has no ticket at position " + book.getNextIndex()));
        TicketEntity ticket = tickets.findByIdForUpdate(drawnTicketId)
                .orElseThrow(() -> new NoSuchElementException("no ticket with id " + drawnTicketId));
        if (ticket.getPlayerId() != null || ticket.getStatus() == TicketStatus.SOLD) {
            throw new IllegalStateException("ticket " + drawnTicketId + " has already been sold");
        }
        ticket.setStatus(TicketStatus.SOLD);
        ticket.setPlayerId(playerId);
        tickets.save(ticket);

        // The player row is locked LAST (after book and ticket), per the writer lock-order rule. An
        // insufficient balance throws here and rolls the ticket write above back with the transaction.
        PlayerEntity playerEntity = players.findByIdForUpdate(playerId)
                .orElseThrow(() -> new NoSuchElementException("no player with id " + playerId));
        Player player = PlayerMapper.toDomain(playerEntity);
        player.debit(price);
        PlayerMapper.applyTo(player, playerEntity);
        players.save(playerEntity);

        book.setNextIndex(book.getNextIndex() + 1);
        books.save(book);
        if (book.getNextIndex() >= book.getTotalTickets()) {
            // Atomic in-place increment: two purchases depleting two different books of the same dealer
            // must not lost-update a read-modify-write of booksDepleted. No DealerEntity is loaded here,
            // so nothing later clobbers the bulk update.
            dealers.incrementBooksDepleted(dealerId);
        }

        recorder.record(new Transaction(
                UUID.randomUUID(), playerId, TransactionType.SPEND, price, dealerId, bookId, ticket.getId(),
                Instant.now()));

        return new PurchaseResult(ticket.getId(), TicketStatus.SOLD, price, dealerId, bookId);
    }
}
