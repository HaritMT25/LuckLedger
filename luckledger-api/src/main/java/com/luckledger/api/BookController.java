package com.luckledger.api;

import com.luckledger.api.persistence.TicketBookEntity;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to book <em>metadata</em>. Deliberately exposes only counts — never the tickets'
 * contents or values — so a client cannot peek at unsold outcomes (the anticipation is the product).
 * There is no flat catalogue of every book: books are reached only through their owning shop (see
 * {@link DealerController}'s {@code /api/dealers/{id}/books}); this endpoint serves a single book by id
 * for the purchase flow.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final GameStore gameStore;

    public BookController(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    @GetMapping("/{bookId}")
    public BookDto get(@PathVariable UUID bookId) {
        TicketBookEntity book = gameStore.book(bookId);
        return toDto(book, DealerController.gameName(gameStore.game(book.getGameId())));
    }

    /** Builds the metadata DTO for a book; shared with {@link DealerController}'s per-shop listing. */
    static BookDto toDto(TicketBookEntity book, String gameName) {
        int remaining = book.getTotalTickets() - book.getNextIndex();
        return new BookDto(book.getId(), book.getDealerId(), book.getGameId(), gameName,
                book.getPoolContractId(), book.getTotalTickets(), remaining);
    }

    /** Metadata only — no ticket list, no per-ticket prize. */
    public record BookDto(UUID bookId, UUID dealerId, UUID gameId, String gameName, UUID poolContractId,
            int totalTickets, int ticketsRemaining) {}
}
