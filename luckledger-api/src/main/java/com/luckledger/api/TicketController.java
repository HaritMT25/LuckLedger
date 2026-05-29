package com.luckledger.api;

import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.RevealGateway.RevealOutcome;
import com.luckledger.domain.scratch.PurchaseResult;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two-step play flow: buy the next ticket from a book ({@code POST /api/books/{id}/purchase}),
 * then later scratch it ({@code POST /api/tickets/{id}/reveal}). A ticket read before reveal is
 * masked (no outcome); after reveal it shows the prize. Reveal is idempotent. Both writes run in the
 * transactional {@link PurchaseGateway}/{@link RevealGateway} against Postgres.
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
        return TicketView.revealed(outcome);
    }

    public record PlayerRequest(UUID playerId) {}

    /** Masked before reveal ({@code isWinner}/{@code prizeAmount} null); full after. */
    public record TicketView(
            UUID ticketId, String mechanic, boolean revealed, Boolean isWinner, BigDecimal prizeAmount) {

        static TicketView masked(TicketEntity ticket) {
            return new TicketView(ticket.getId(), ticket.getMechanicType().name(), false, null, null);
        }

        static TicketView revealed(TicketEntity ticket) {
            return new TicketView(
                    ticket.getId(),
                    ticket.getMechanicType().name(),
                    true,
                    ticket.getRevealedIsWinner(),
                    ticket.getRevealedPrize());
        }

        static TicketView revealed(RevealOutcome outcome) {
            return new TicketView(
                    outcome.ticketId(),
                    outcome.mechanicType().name(),
                    true,
                    outcome.winner(),
                    outcome.prizeAmount());
        }
    }
}
