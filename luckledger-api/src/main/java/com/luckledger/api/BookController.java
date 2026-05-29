package com.luckledger.api;

import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.TicketBookEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to book <em>metadata</em>. Deliberately exposes only counts — never the tickets'
 * contents or values — so a client cannot peek at unsold outcomes (the anticipation is the product).
 * Each book also carries its owning shop ({@code dealerId}) and game, so a client can group a shop's
 * books by game.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final GameStore gameStore;

    public BookController(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    @GetMapping
    public List<BookDto> list() {
        Map<UUID, String> gameNames = gameStore.games().stream()
                .collect(Collectors.toMap(GameEntity::getId, DealerController::gameName));
        return gameStore.books().stream()
                .map(b -> dto(b, gameNames.getOrDefault(b.getGameId(), "Unknown game")))
                .toList();
    }

    @GetMapping("/{bookId}")
    public BookDto get(@PathVariable UUID bookId) {
        TicketBookEntity book = gameStore.book(bookId);
        return dto(book, DealerController.gameName(gameStore.game(book.getGameId())));
    }

    private static BookDto dto(TicketBookEntity book, String gameName) {
        int remaining = book.getTotalTickets() - book.getNextIndex();
        return new BookDto(book.getId(), book.getDealerId(), book.getGameId(), gameName,
                book.getPoolContractId(), book.getTotalTickets(), remaining);
    }

    /** Metadata only — no ticket list, no per-ticket prize. */
    public record BookDto(UUID bookId, UUID dealerId, UUID gameId, String gameName, UUID poolContractId,
            int totalTickets, int ticketsRemaining) {}
}
