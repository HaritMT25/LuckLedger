package com.luckledger.api;

import com.luckledger.api.persistence.DealerEntity;
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

        TicketBookEntity book = books.findById(bookId)
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
        BigDecimal price = game.getTicketPrice();

        // Debit first: throws InsufficientBalanceException before a ticket is drawn.
        PlayerEntity playerEntity = players.findById(playerId)
                .orElseThrow(() -> new NoSuchElementException("no player with id " + playerId));
        Player player = PlayerMapper.toDomain(playerEntity);
        player.debit(price);
        PlayerMapper.applyTo(player, playerEntity);
        players.save(playerEntity);

        TicketEntity ticket = tickets.findByBookIdAndPositionInBook(bookId, book.getNextIndex())
                .orElseThrow(() -> new NoSuchElementException(
                        "book " + bookId + " has no ticket at position " + book.getNextIndex()));
        ticket.setStatus(TicketStatus.SOLD);
        ticket.setPlayerId(playerId);
        tickets.save(ticket);

        book.setNextIndex(book.getNextIndex() + 1);
        books.save(book);
        if (book.getNextIndex() >= book.getTotalTickets()) {
            DealerEntity dealer = dealers.findById(dealerId)
                    .orElseThrow(() -> new NoSuchElementException("no dealer with id " + dealerId));
            dealer.setBooksDepleted(dealer.getBooksDepleted() + 1);
            dealers.save(dealer);
        }

        recorder.record(new Transaction(
                UUID.randomUUID(), playerId, TransactionType.SPEND, price, dealerId, bookId, ticket.getId(),
                Instant.now()));

        return new PurchaseResult(ticket.getId(), TicketStatus.SOLD, price, dealerId, bookId);
    }
}
