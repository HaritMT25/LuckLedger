package com.luckledger.api;

import com.luckledger.api.persistence.TicketBookEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to book <em>metadata</em>. Deliberately exposes only counts — never the tickets'
 * contents or values — so a client cannot peek at unsold outcomes (the anticipation is the product).
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
        return gameStore.books().stream().map(BookController::dto).toList();
    }

    @GetMapping("/{bookId}")
    public BookDto get(@PathVariable UUID bookId) {
        return dto(gameStore.book(bookId));
    }

    private static BookDto dto(TicketBookEntity book) {
        int remaining = book.getTotalTickets() - book.getNextIndex();
        return new BookDto(book.getId(), book.getPoolContractId(), book.getTotalTickets(), remaining);
    }

    /** Metadata only — no ticket list, no per-ticket prize. */
    public record BookDto(UUID bookId, UUID poolContractId, int totalTickets, int ticketsRemaining) {}
}
