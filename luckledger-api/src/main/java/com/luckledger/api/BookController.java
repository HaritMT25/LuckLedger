package com.luckledger.api;

import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketRepository.BookTicketStats;
import com.luckledger.domain.generation.MetadataVisibility;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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
 *
 * <p>Each book carries a {@link MetadataVisibility} tier that decides how much of its depletion state
 * the DTO discloses. Enforcement is <strong>server-side</strong> in {@link #toDto}: a NONE book never
 * has its prize/dispensed data serialized at all, so the client cannot receive (let alone display) data
 * it should not see. None of this changes any per-ticket odds — the pool was fixed at print time.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final GameStore gameStore;

    public BookController(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    /**
     * Returns metadata for one book, with its depletion data gated by the book's visibility tier.
     *
     * @param bookId the book's id
     * @return the book metadata DTO (visibility-appropriate fields only)
     */
    @GetMapping("/{bookId}")
    public BookDto get(@PathVariable UUID bookId) {
        TicketBookEntity book = gameStore.book(bookId);
        BookTicketStats stats = gameStore.bookStats(List.of(bookId)).get(bookId);
        return toDto(book, gameStore.game(book.getGameId()), stats);
    }

    /**
     * Builds the metadata DTO for a book; shared with {@link DealerController}'s per-shop listing.
     *
     * <p>The book's {@link MetadataVisibility} tier gates the depletion data server-side:
     * <ul>
     *   <li>{@code NONE} — counts only; all four data fields are {@code null}.
     *   <li>{@code PARTIAL} — additionally the percentage of the book already dispensed (sold).
     *   <li>{@code FULL} — additionally the prize value dispensed, the value estimated to remain, and how
     *       many of the revealed tickets have won.
     * </ul>
     *
     * @param book the book to describe; never {@code null}
     * @param game the book's game (for name/mechanic/price); never {@code null}
     * @param stats the book's aggregated ticket economics, or {@code null} if none were loaded (treated
     *     as all-zero — only consulted for a FULL book)
     */
    static BookDto toDto(TicketBookEntity book, GameEntity game, BookTicketStats stats) {
        int remaining = book.getTotalTickets() - book.getNextIndex();
        MetadataVisibility visibility = book.getMetadataVisibility();

        BigDecimal percentDispensed = null;
        BigDecimal prizesDispensed = null;
        BigDecimal estimatedRemainingValue = null;
        Long winFrequencySoFar = null;

        if (visibility == MetadataVisibility.PARTIAL || visibility == MetadataVisibility.FULL) {
            percentDispensed = percentDispensed(book);
        }
        if (visibility == MetadataVisibility.FULL) {
            BigDecimal prizeFund = stats == null ? BigDecimal.ZERO : stats.getPrizeFund();
            BigDecimal dispensed = stats == null ? BigDecimal.ZERO : stats.getDispensed();
            prizesDispensed = dispensed.setScale(4, RoundingMode.HALF_UP);
            estimatedRemainingValue = prizeFund.subtract(dispensed).setScale(4, RoundingMode.HALF_UP);
            winFrequencySoFar = stats == null ? 0L : stats.getWinsSoFar();
        }

        return new BookDto(book.getId(), book.getDealerId(), book.getGameId(),
                DealerController.gameName(game), game.getMechanicType().name(), game.getTicketPrice(),
                book.getPoolContractId(), book.getTotalTickets(), remaining, visibility.name(),
                percentDispensed, prizesDispensed, estimatedRemainingValue, winFrequencySoFar);
    }

    /** Percentage (0–100, scale 4) of the book's tickets already sold — the sale cursor over the total. */
    private static BigDecimal percentDispensed(TicketBookEntity book) {
        if (book.getTotalTickets() == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(book.getNextIndex())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(book.getTotalTickets()), 4, RoundingMode.HALF_UP);
    }

    /**
     * Metadata plus the up-front price — still no ticket list and no per-ticket prize. The depletion
     * fields are visibility-gated and therefore nullable: {@code visibility} is always present, but
     * {@code percentDispensed} is null below PARTIAL and the remaining three are null below FULL.
     */
    public record BookDto(UUID bookId, UUID dealerId, UUID gameId, String gameName, String mechanic,
            BigDecimal ticketPrice, UUID poolContractId, int totalTickets, int ticketsRemaining,
            String visibility, BigDecimal percentDispensed, BigDecimal prizesDispensed,
            BigDecimal estimatedRemainingValue, Long winFrequencySoFar) {}
}
