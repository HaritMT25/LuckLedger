package com.luckledger.api;

import com.luckledger.api.persistence.GridCodec;
import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.api.RevealGateway.RevealOutcome;
import com.luckledger.domain.scratch.PurchaseResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
    private final RevealNarrator revealNarrator;

    public TicketController(GameStore gameStore, PlayerRegistry playerRegistry, TicketRepository tickets,
            PurchaseGateway purchaseGateway, RevealGateway revealGateway, RevealNarrator revealNarrator) {
        this.gameStore = gameStore;
        this.playerRegistry = playerRegistry;
        this.tickets = tickets;
        this.purchaseGateway = purchaseGateway;
        this.revealGateway = revealGateway;
        this.revealNarrator = revealNarrator;
    }

    @PostMapping("/api/books/{bookId}/purchase")
    public PurchaseResult purchase(@PathVariable UUID bookId, @Valid @RequestBody PlayerRequest request) {
        return purchaseGateway.purchase(bookId, request.playerId());
    }

    @GetMapping("/api/tickets/{ticketId}")
    public TicketView get(@PathVariable UUID ticketId) {
        TicketEntity ticket = gameStore.ticket(ticketId);
        if (!ticket.isRevealed()) {
            return TicketView.masked(ticket);
        }
        OutcomeNarrative narrative = revealNarrator.narrate(
                ticket.getGrid(), ticket.getMechanicType(), ticket.getPrizeAmount(), ticket.getId());
        return TicketView.revealed(ticket, narrative);
    }

    @PostMapping("/api/tickets/{ticketId}/reveal")
    public TicketView reveal(@PathVariable UUID ticketId, @Valid @RequestBody PlayerRequest request) {
        RevealOutcome outcome = revealGateway.reveal(ticketId, request.playerId());
        OutcomeNarrative narrative = revealNarrator.narrate(
                outcome.grid(), outcome.mechanicType(), outcome.prizeAmount(), outcome.ticketId());
        return TicketView.revealed(outcome, narrative);
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

    public record PlayerRequest(@NotNull UUID playerId) {}

    /** A bought ticket awaiting its scratch — enough to resume the scratch flow. */
    public record PendingTicket(UUID ticketId, String mechanic, UUID gameId, String gameName, UUID bookId) {}

    /**
     * Masked before reveal ({@code isWinner}/{@code prizeAmount}/{@code grid}/{@code narrative} null);
     * full after. The grid is the themed grid persisted at generation time — the actual symbols under
     * the coating. The {@code narrative} is a read-only, education-layer explanation of the outcome;
     * it is null before reveal and null if the sacred-payout guard tripped, and it never affects the
     * prize (that always follows the stored amount).
     *
     * <p><strong>Commit-reveal proof.</strong> {@code gridCommitment} (the public SHA-256 of the grid)
     * is present in <em>both</em> the masked and revealed views — the player sees the outcome was fixed
     * before purchase. {@code commitmentSalt} is the secret that would let the commitment be verified;
     * it is <strong>absent (null) on the masked view</strong> and only supplied once revealed, otherwise
     * a client could brute-force the small grid space from the commitment and learn the outcome early.
     * Both are null for legacy tickets.
     */
    public record TicketView(
            UUID ticketId, UUID gameId, String mechanic, boolean revealed, Boolean isWinner,
            BigDecimal prizeAmount, GridCodec.ThemedGridDto grid, OutcomeNarrative narrative,
            String gridCommitment, String commitmentSalt) {

        static TicketView masked(TicketEntity ticket) {
            // gridCommitment present, commitmentSalt DELIBERATELY null: the salt must not leak pre-reveal.
            return new TicketView(
                    ticket.getId(), ticket.getGameId(), ticket.getMechanicType().name(), false, null, null,
                    null, null, ticket.getGridCommitment(), null);
        }

        static TicketView revealed(TicketEntity ticket, OutcomeNarrative narrative) {
            return new TicketView(
                    ticket.getId(),
                    ticket.getGameId(),
                    ticket.getMechanicType().name(),
                    true,
                    ticket.getRevealedIsWinner(),
                    ticket.getRevealedPrize(),
                    ticket.getSkinnedGrid(),
                    narrative,
                    ticket.getGridCommitment(),
                    ticket.getCommitmentSalt());
        }

        static TicketView revealed(RevealOutcome outcome, OutcomeNarrative narrative) {
            return new TicketView(
                    outcome.ticketId(),
                    outcome.gameId(),
                    outcome.mechanicType().name(),
                    true,
                    outcome.winner(),
                    outcome.prizeAmount(),
                    outcome.skinnedGrid(),
                    narrative,
                    outcome.gridCommitment(),
                    outcome.commitmentSalt());
        }
    }
}
