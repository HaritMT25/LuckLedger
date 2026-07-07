package com.luckledger.api;

import com.luckledger.api.persistence.PlayerRepository;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.domain.player.Player;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The master account's control surface — every endpoint requires {@code ROLE_MASTER} (enforced both
 * by the {@link SecurityConfig} URL rules and the method-security annotation here, so a config
 * regression cannot silently open it). Control is deliberately shaped by the domain invariants:
 * the master can <em>see</em> everything (players, balances, pool economics), <em>grant</em> coins
 * (recorded as an ordinary BORROW — the ledger stays append-only), and <em>restock</em> games
 * (a real operator action through the verified generation pipeline). There is no endpoint to edit
 * balances in place, delete history, or change a payout ratio — those would break the invariants
 * the simulator exists to teach.
 */
@RestController
@RequestMapping("/api/master")
@PreAuthorize("hasRole('MASTER')")
public class MasterController {

    private final PlayerRepository players;
    private final TicketRepository tickets;
    private final PlayerRegistry playerRegistry;
    private final RestockService restockService;

    public MasterController(PlayerRepository players, TicketRepository tickets, PlayerRegistry playerRegistry,
            RestockService restockService) {
        this.players = players;
        this.tickets = tickets;
        this.playerRegistry = playerRegistry;
        this.restockService = restockService;
    }

    /** Every player, with bankroll totals and how many bought tickets still sit unscratched. */
    @GetMapping("/players")
    public List<PlayerAdminView> listPlayers() {
        Map<UUID, Long> pending = tickets.countPendingByPlayer().stream()
                .collect(Collectors.toMap(
                        TicketRepository.PendingByPlayer::getPlayerId,
                        TicketRepository.PendingByPlayer::getPending));
        return players.findAll().stream()
                .map(p -> new PlayerAdminView(
                        p.getId(),
                        p.getDisplayName(),
                        p.getCoinBalance(),
                        p.getTotalBorrowed(),
                        p.getTotalSpent(),
                        p.getTotalWon(),
                        p.getTotalWon().subtract(p.getTotalSpent()), // same formula as Player.getNetPosition
                        p.getTicketCount(),
                        pending.getOrDefault(p.getId(), 0L),
                        p.getCreatedAt()))
                .toList();
    }

    /**
     * Grants a player coins. Implemented as a bank loan so the move is visible in the player's own
     * ledger like any other coin movement — the ledger stays append-only and complete. The amount is
     * validated at the boundary ({@code @Valid}: positive, bounded, at most 4 decimal places).
     */
    @PostMapping("/players/{playerId}/grant")
    public PlayerController.PlayerDto grant(@PathVariable UUID playerId, @Valid @RequestBody GrantRequest request) {
        Player player = playerRegistry.borrow(playerId, request.amount());
        return new PlayerController.PlayerDto(
                player.getPlayerId(), player.getDisplayName(), player.getCoinBalance(),
                player.getTotalBorrowed(), player.getTotalSpent(), player.getTotalWon(),
                player.getNetPosition());
    }

    /** Generates and allocates a fresh batch of books for a game (see {@link RestockService}). */
    @PostMapping("/games/{gameId}/restock")
    public RestockService.RestockResult restock(@PathVariable UUID gameId) {
        return restockService.restock(gameId);
    }

    public record GrantRequest(
            @NotNull @Positive @Digits(integer = 9, fraction = 4) @DecimalMax("1000000") BigDecimal amount) {}

    public record PlayerAdminView(UUID playerId, String displayName, BigDecimal coinBalance,
            BigDecimal totalBorrowed, BigDecimal totalSpent, BigDecimal totalWon, BigDecimal netPosition,
            int ticketCount, long pendingTickets, Instant createdAt) {}
}
