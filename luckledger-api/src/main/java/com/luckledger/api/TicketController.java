package com.luckledger.api;

import com.luckledger.api.persistence.GridCodec;
import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.persistence.TicketRepository;
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
 * masked — no outcome and <em>no grid</em>, so a client cannot evaluate the ticket early; the themed
 * grid (the player's real symbols) is served only once the ticket is revealed. Reveal is idempotent.
 * Both writes run in the transactional {@link PurchaseGateway}/{@link RevealGateway} against Postgres.
 *
 * <p>{@code GET /api/players/{id}/tickets} lists a player's bought-but-unscratched tickets so the
 * frontend can recover a pending ticket after a refresh (the purchase is already paid for).
 */
@RestController
public class TicketController {

    private final GameStore gameStore;
    private final PlayerRegistry playerRegistry;
    private final TicketRepository tickets;
    private final PurchaseGateway purchaseGateway;
    private final RevealGateway revealGateway;

    public TicketController(GameStore gameStore, PlayerRegistry playerRegistry, TicketRepository tickets,
            PurchaseGateway purchaseGateway, RevealGateway revealGateway) {
        this.gameStore = gameStore;
        this.playerRegistry = playerRegistry;
        this.tickets = tickets;
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

    /** A player's bought-but-unscratched tickets, oldest first. 404 if the player does not exist. */
    @GetMapping("/api/players/{playerId}/tickets")
    public List<PendingTicket> pendingTickets(@PathVariable UUID playerId) {
        playerRegistry.get(playerId); // 404 if unknown
        return tickets.findByPlayerIdAndRevealedFalseOrderByIdAsc(playerId).stream()
                .map(t -> new PendingTicket(
                        t.getId(),
                        t.getMechanicType().name(),
                        t.getGameId(),
                        DealerController.gameName(gameStore.game(t.getGameId())),
                        t.getBookId()))
                .toList();
    }

    public record PlayerRequest(UUID playerId) {}

    /** A bought ticket awaiting its scratch — enough to resume the scratch flow. */
    public record PendingTicket(UUID ticketId, String mechanic, UUID gameId, String gameName, UUID bookId) {}

    /**
     * Masked before reveal ({@code isWinner}/{@code prizeAmount}/{@code grid} null); full after. The
     * grid is the themed grid persisted at generation time — the actual symbols under the coating.
     */
    public record TicketView(
            UUID ticketId, UUID gameId, String mechanic, boolean revealed, Boolean isWinner,
            BigDecimal prizeAmount, GridCodec.ThemedGridDto grid) {

        static TicketView masked(TicketEntity ticket) {
            return new TicketView(
                    ticket.getId(), ticket.getGameId(), ticket.getMechanicType().name(), false, null, null, null);
        }

        static TicketView revealed(TicketEntity ticket) {
            return new TicketView(
                    ticket.getId(),
                    ticket.getGameId(),
                    ticket.getMechanicType().name(),
                    true,
                    ticket.getRevealedIsWinner(),
                    ticket.getRevealedPrize(),
                    ticket.getSkinnedGrid());
        }

        static TicketView revealed(RevealOutcome outcome) {
            return new TicketView(
                    outcome.ticketId(),
                    outcome.gameId(),
                    outcome.mechanicType().name(),
                    true,
                    outcome.winner(),
                    outcome.prizeAmount(),
                    outcome.skinnedGrid());
        }
    }
}
