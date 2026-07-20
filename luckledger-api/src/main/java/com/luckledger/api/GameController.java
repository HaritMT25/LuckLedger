package com.luckledger.api;

import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.domain.generation.verification.NearMissReport;
import com.luckledger.domain.generation.verification.VerificationReport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to the pre-generated games. There is no live game creation; games are seeded at
 * startup and exposed here for listing, summary, and the verification/near-miss evidence.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameStore gameStore;
    private final TicketRepository tickets;

    public GameController(GameStore gameStore, TicketRepository tickets) {
        this.gameStore = gameStore;
        this.tickets = tickets;
    }

    @GetMapping
    public List<GameSummary> list() {
        Map<UUID, BigDecimal> topPrizes = topPrizes();
        return gameStore.games().stream().map(g -> summarize(g, topPrizes)).toList();
    }

    @GetMapping("/{gameId}")
    public GameSummary get(@PathVariable UUID gameId) {
        return summarize(gameStore.game(gameId), topPrizes());
    }

    /** Highest predetermined prize per game — what a ticket advertises as "top prize". */
    private Map<UUID, BigDecimal> topPrizes() {
        return tickets.aggregateByGame().stream()
                .collect(Collectors.toMap(
                        TicketRepository.GameTicketStats::getGameId,
                        TicketRepository.GameTicketStats::getTopPrize));
    }

    @GetMapping("/{gameId}/verification")
    public VerificationDto verification(@PathVariable UUID gameId) {
        VerificationReport report = gameStore.game(gameId).getVerificationReport();
        List<CheckDto> checks = report.checks().stream()
                .map(c -> new CheckDto(c.name(), c.passed(), c.message()))
                .toList();
        return new VerificationDto(report.passed(), checks);
    }

    @GetMapping("/{gameId}/near-misses")
    public NearMissDto nearMisses(@PathVariable UUID gameId) {
        NearMissReport report = gameStore.game(gameId).getNearMiss();
        return new NearMissDto(
                report.totalLosers(), report.nearMissCount(), report.nearMissRate(), report.distribution());
    }

    private static GameSummary summarize(GameEntity game, Map<UUID, BigDecimal> topPrizes) {
        return new GameSummary(
                game.getId(),
                DealerController.gameName(game),
                game.getName(),
                game.getStatus().name(),
                game.getMechanicType().name(),
                game.getTicketPrice(),
                game.getPayoutRatio(),
                topPrizes.getOrDefault(game.getId(), BigDecimal.ZERO),
                game.getTotalTickets(),
                game.getDealerCount(),
                game.isVerificationPassed());
    }

    /**
     * A game's headline economics. {@code gameName} is the always-present display name (derived from the
     * mechanic); {@code name} is the operator-chosen campaign name (null for legacy games); {@code status}
     * is the lifecycle state (ACTIVE/RETIRED).
     */
    public record GameSummary(
            UUID gameId, String gameName, String name, String status, String mechanic, BigDecimal ticketPrice,
            BigDecimal payoutRatio, BigDecimal topPrize, int ticketCount, int dealerCount,
            boolean verificationPassed) {}

    public record VerificationDto(boolean passed, List<CheckDto> checks) {}

    public record CheckDto(String name, boolean passed, String message) {}

    public record NearMissDto(
            int totalLosers, int nearMissCount, double nearMissRate, Map<Integer, Integer> distribution) {}
}
