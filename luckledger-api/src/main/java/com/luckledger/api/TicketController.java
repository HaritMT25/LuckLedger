package com.luckledger.api;

import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.player.Player;
import com.luckledger.domain.scratch.PurchaseResult;
import com.luckledger.domain.scratch.RevealResult;
import com.luckledger.scratchflow.ScratchRevealService;
import com.luckledger.scratchflow.TicketPurchaseService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two-step play flow: buy the next ticket from a book ({@code POST /api/books/{id}/purchase}),
 * then later scratch it ({@code POST /api/tickets/{id}/reveal}). A ticket read before reveal is
 * masked (no outcome); after reveal it shows the prize. Reveal is idempotent.
 */
@RestController
public class TicketController {

    private final GameStore gameStore;
    private final PlayerRegistry players;
    private final TicketPurchaseService purchaseService;
    private final Map<MechanicType, ScratchRevealService> revealServices;

    public TicketController(
            GameStore gameStore,
            PlayerRegistry players,
            TicketPurchaseService purchaseService,
            Map<MechanicType, ScratchRevealService> revealServices) {
        this.gameStore = gameStore;
        this.players = players;
        this.purchaseService = purchaseService;
        this.revealServices = revealServices;
    }

    @PostMapping("/api/books/{bookId}/purchase")
    public PurchaseResult purchase(@PathVariable UUID bookId, @RequestBody PlayerRequest request) {
        Player player = players.get(request.playerId());
        BigDecimal price = gameStore.ticketPrice(bookId);
        return purchaseService.purchase(
                player, gameStore.dealerForBook(bookId), gameStore.book(bookId), price);
    }

    @GetMapping("/api/tickets/{ticketId}")
    public TicketView get(@PathVariable UUID ticketId) {
        TicketCard card = gameStore.ticket(ticketId);
        MechanicType mechanic = card.layout().mechanicType();
        try {
            RevealResult revealed = reveals(mechanic).getRevealedResult(ticketId);
            return TicketView.revealed(card, revealed);
        } catch (NoSuchElementException notRevealed) {
            return TicketView.masked(card);
        }
    }

    @PostMapping("/api/tickets/{ticketId}/reveal")
    public TicketView reveal(@PathVariable UUID ticketId, @RequestBody PlayerRequest request) {
        Player player = players.get(request.playerId());
        TicketCard card = gameStore.ticket(ticketId);
        RevealResult result = reveals(card.layout().mechanicType()).reveal(player, card);
        return TicketView.revealed(card, result);
    }

    private ScratchRevealService reveals(MechanicType mechanic) {
        ScratchRevealService service = revealServices.get(mechanic);
        if (service == null) {
            throw new NoSuchElementException("no reveal service for mechanic " + mechanic);
        }
        return service;
    }

    public record PlayerRequest(UUID playerId) {}

    /** Masked before reveal ({@code isWinner}/{@code prizeAmount} null); full after. */
    public record TicketView(
            UUID ticketId, String mechanic, boolean revealed, Boolean isWinner, BigDecimal prizeAmount) {

        static TicketView masked(TicketCard card) {
            return new TicketView(card.ticketId(), card.layout().mechanicType().name(), false, null, null);
        }

        static TicketView revealed(TicketCard card, RevealResult result) {
            return new TicketView(
                    card.ticketId(),
                    card.layout().mechanicType().name(),
                    true,
                    result.isWinner(),
                    result.prizeAmount());
        }
    }
}
