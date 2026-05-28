package com.luckledger.api;

import com.luckledger.distribution.Dealer;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.distribution.TicketBook;
import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.orchestration.GameConfig;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory registry of the pre-generated games and by-id indexes over their books, dealers, and
 * tickets. Games are seeded at startup (no live {@code POST /games}); everything else looks up by id.
 *
 * <p>Indexing books/dealers/tickets here keeps the controllers thin and lets a purchase resolve the
 * owning dealer and the game's ticket price (which lives on the {@link GameConfig}'s pool, not on the
 * {@link TicketBook}). Only <em>allocated</em> books are purchasable, so the book/dealer indexes are
 * built from the allocation map.
 */
@Service
public class GameStore {

    /** A registered game: its id, the config it was built from, and the resulting setup. */
    public record StoredGame(UUID gameId, GameConfig config, GameSetupResult setup) {}

    private final Map<UUID, StoredGame> games = new ConcurrentHashMap<>();
    private final Map<UUID, TicketBook> booksById = new ConcurrentHashMap<>();
    private final Map<UUID, Dealer> dealersById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> bookToGame = new ConcurrentHashMap<>();
    private final Map<UUID, Dealer> bookToDealer = new ConcurrentHashMap<>();
    private final Map<UUID, TicketCard> ticketsById = new ConcurrentHashMap<>();

    /**
     * Registers a pre-generated game and indexes its dealers, allocated books, and tickets.
     *
     * @param config the config the game was built from (carries the ticket price); never {@code null}
     * @param setup the generated/allocated game; never {@code null}
     * @return the assigned game id
     */
    public UUID register(GameConfig config, GameSetupResult setup) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(setup, "setup must not be null");
        UUID gameId = UUID.randomUUID();
        games.put(gameId, new StoredGame(gameId, config, setup));
        setup.dealers().forEach(dealer -> dealersById.put(dealer.dealerId(), dealer));
        setup.allocationMap().forEach((dealer, books) -> books.forEach(book -> {
            booksById.put(book.bookId(), book);
            bookToGame.put(book.bookId(), gameId);
            bookToDealer.put(book.bookId(), dealer);
        }));
        setup.generationResult().tickets().forEach(card -> ticketsById.put(card.ticketId(), card));
        return gameId;
    }

    public StoredGame game(UUID gameId) {
        return require(games.get(Objects.requireNonNull(gameId, "gameId")), "game", gameId);
    }

    public Collection<StoredGame> games() {
        return List.copyOf(games.values());
    }

    public TicketBook book(UUID bookId) {
        return require(booksById.get(Objects.requireNonNull(bookId, "bookId")), "book", bookId);
    }

    public Collection<TicketBook> books() {
        return List.copyOf(booksById.values());
    }

    public Dealer dealerForBook(UUID bookId) {
        return require(bookToDealer.get(Objects.requireNonNull(bookId, "bookId")), "book", bookId);
    }

    /** The ticket price of the game that owns the book. */
    public BigDecimal ticketPrice(UUID bookId) {
        UUID gameId = require(bookToGame.get(Objects.requireNonNull(bookId, "bookId")), "book", bookId);
        return game(gameId).config().poolContract().ticketPrice();
    }

    public Dealer dealer(UUID dealerId) {
        return require(dealersById.get(Objects.requireNonNull(dealerId, "dealerId")), "dealer", dealerId);
    }

    public Collection<Dealer> dealers() {
        return List.copyOf(dealersById.values());
    }

    public TicketCard ticket(UUID ticketId) {
        return require(ticketsById.get(Objects.requireNonNull(ticketId, "ticketId")), "ticket", ticketId);
    }

    /** The mechanic a game was generated with, read from its first ticket. */
    public static MechanicType mechanicOf(GameSetupResult setup) {
        return setup.generationResult().tickets().get(0).layout().mechanicType();
    }

    private static <T> T require(T value, String kind, UUID id) {
        if (value == null) {
            throw new NoSuchElementException("no " + kind + " with id " + id);
        }
        return value;
    }
}
