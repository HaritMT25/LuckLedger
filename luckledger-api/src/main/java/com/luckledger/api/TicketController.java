package com.luckledger.api;

import com.luckledger.api.persistence.GridCodec.CellDto;
import com.luckledger.api.persistence.GridCodec.GridDto;
import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.RevealGateway.RevealOutcome;
import com.luckledger.domain.scratch.PurchaseResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two-step play flow: buy the next ticket from a book ({@code POST /api/books/{id}/purchase}),
 * then later scratch it ({@code POST /api/tickets/{id}/reveal}). A ticket read before reveal is
 * masked (no outcome and no grid — so a client cannot peek); after reveal it shows the prize and the
 * symbol grid so the client can render the real numbers/seals under each scratch zone. Reveal is
 * idempotent. Both writes run in the transactional {@link PurchaseGateway}/{@link RevealGateway}.
 */
@RestController
public class TicketController {

    private final GameStore gameStore;
    private final PurchaseGateway purchaseGateway;
    private final RevealGateway revealGateway;

    public TicketController(GameStore gameStore, PurchaseGateway purchaseGateway, RevealGateway revealGateway) {
        this.gameStore = gameStore;
        this.purchaseGateway = purchaseGateway;
        this.revealGateway = revealGateway;
    }

    @PostMapping("/api/books/{bookId}/purchase")
    public PurchaseResult purchase(@PathVariable UUID bookId, @RequestBody PlayerRequest request) {
        return purchaseGateway.purchase(bookId, request.playerId());
    }

    @GetMapping("/api/tickets/{ticketId}")
    public TicketView get(@PathVariable UUID ticketId) {
        TicketEntity ticket = gameStore.ticket(ticketId);
        return ticket.isRevealed() ? TicketView.revealed(ticket) : TicketView.masked(ticket);
    }

    @PostMapping("/api/tickets/{ticketId}/reveal")
    public TicketView reveal(@PathVariable UUID ticketId, @RequestBody PlayerRequest request) {
        RevealOutcome outcome = revealGateway.reveal(ticketId, request.playerId());
        // The grid was generated and verified at seed time; serve it now that the ticket is revealed.
        return TicketView.revealed(outcome, gameStore.ticket(ticketId).getGrid());
    }

    public record PlayerRequest(UUID playerId) {}

    /** A single grid cell's revealed symbol (the number for Celestial, the seal for Demon). */
    public record CellView(int row, int col, String symbol) {}

    /** Masked before reveal ({@code isWinner}/{@code prizeAmount}/{@code grid} null); full after. */
    public record TicketView(
            UUID ticketId, String mechanic, boolean revealed, Boolean isWinner, BigDecimal prizeAmount,
            List<CellView> grid) {

        static TicketView masked(TicketEntity ticket) {
            return new TicketView(ticket.getId(), ticket.getMechanicType().name(), false, null, null, null);
        }

        static TicketView revealed(TicketEntity ticket) {
            return new TicketView(
                    ticket.getId(),
                    ticket.getMechanicType().name(),
                    true,
                    ticket.getRevealedIsWinner(),
                    ticket.getRevealedPrize(),
                    cells(ticket.getGrid()));
        }

        static TicketView revealed(RevealOutcome outcome, GridDto grid) {
            return new TicketView(
                    outcome.ticketId(),
                    outcome.mechanicType().name(),
                    true,
                    outcome.winner(),
                    outcome.prizeAmount(),
                    cells(grid));
        }

        private static List<CellView> cells(GridDto grid) {
            if (grid == null) {
                return List.of();
            }
            return grid.cells().stream()
                    .map((CellDto c) -> new CellView(c.row(), c.col(), c.symbol()))
                    .toList();
        }
    }
}
