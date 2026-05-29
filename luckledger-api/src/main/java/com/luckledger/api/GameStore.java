package com.luckledger.api;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.persistence.TicketRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only facade over the persisted games and their dealers, books, and tickets. Replaces the
 * former in-memory registry: every lookup hits Postgres via the repositories.
 *
 * <p>Only <em>allocated</em> books (those assigned to a dealer) are exposed and purchasable — an
 * unallocated book is not visible here, matching the prior in-memory behaviour where only books in
 * the allocation map were indexed.
 */
@Service
@Transactional(readOnly = true)
public class GameStore {

    private final GameRepository games;
    private final DealerRepository dealers;
    private final TicketBookRepository books;
    private final TicketRepository tickets;

    public GameStore(GameRepository games, DealerRepository dealers, TicketBookRepository books,
            TicketRepository tickets) {
        this.games = games;
        this.dealers = dealers;
        this.books = books;
        this.tickets = tickets;
    }

    public List<GameEntity> games() {
        return games.findAll();
    }

    public GameEntity game(UUID gameId) {
        Objects.requireNonNull(gameId, "gameId");
        return games.findById(gameId).orElseThrow(() -> notFound("game", gameId));
    }

    /** All allocated (purchasable) books. */
    public List<TicketBookEntity> books() {
        return books.findAll().stream().filter(b -> b.getDealerId() != null).toList();
    }

    /** An allocated book by id; a non-existent or unallocated book is treated as not found. */
    public TicketBookEntity book(UUID bookId) {
        Objects.requireNonNull(bookId, "bookId");
        TicketBookEntity book = books.findById(bookId).filter(b -> b.getDealerId() != null).orElse(null);
        if (book == null) {
            throw notFound("book", bookId);
        }
        return book;
    }

    /** The dealer that owns an allocated book. */
    public DealerEntity dealerForBook(UUID bookId) {
        return dealer(book(bookId).getDealerId());
    }

    public List<DealerEntity> dealers() {
        return dealers.findAll();
    }

    public DealerEntity dealer(UUID dealerId) {
        Objects.requireNonNull(dealerId, "dealerId");
        return dealers.findById(dealerId).orElseThrow(() -> notFound("dealer", dealerId));
    }

    public TicketEntity ticket(UUID ticketId) {
        Objects.requireNonNull(ticketId, "ticketId");
        return tickets.findById(ticketId).orElseThrow(() -> notFound("ticket", ticketId));
    }

    /** How many of a dealer's books are still selling (cursor has not reached the end). */
    public int activeBookCount(UUID dealerId) {
        return (int) books.findByDealerId(dealerId).stream()
                .filter(b -> b.getNextIndex() < b.getTotalTickets())
                .count();
    }

    private static NoSuchElementException notFound(String kind, UUID id) {
        return new NoSuchElementException("no " + kind + " with id " + id);
    }
}
