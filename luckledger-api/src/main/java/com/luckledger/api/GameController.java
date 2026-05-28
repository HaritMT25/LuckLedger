package com.luckledger.api;

import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.generation.verification.NearMissReport;
import com.luckledger.domain.generation.verification.VerificationReport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public GameController(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    @GetMapping
    public List<GameSummary> list() {
        return gameStore.games().stream()
                .map(g -> summarize(g.gameId(), g.setup()))
                .toList();
    }

    @GetMapping("/{gameId}")
    public GameSummary get(@PathVariable UUID gameId) {
        return summarize(gameId, gameStore.game(gameId).setup());
    }

    @GetMapping("/{gameId}/verification")
    public VerificationDto verification(@PathVariable UUID gameId) {
        VerificationReport report = gameStore.game(gameId).setup().generationResult().verificationReport();
        List<CheckDto> checks = report.checks().stream()
                .map(c -> new CheckDto(c.name(), c.passed(), c.message()))
                .toList();
        return new VerificationDto(report.passed(), checks);
    }

    @GetMapping("/{gameId}/near-misses")
    public NearMissDto nearMisses(@PathVariable UUID gameId) {
        NearMissReport report = gameStore.game(gameId).setup().generationResult().nearMissReport();
        return new NearMissDto(
                report.totalLosers(), report.nearMissCount(), report.nearMissRate(), report.distribution());
    }

    private static GameSummary summarize(UUID gameId, GameSetupResult setup) {
        return new GameSummary(
                gameId,
                GameStore.mechanicOf(setup).name(),
                setup.generationResult().tickets().size(),
                setup.dealers().size(),
                setup.generationResult().verificationReport().passed());
    }

    public record GameSummary(
            UUID gameId, String mechanic, int ticketCount, int dealerCount, boolean verificationPassed) {}

    public record VerificationDto(boolean passed, List<CheckDto> checks) {}

    public record CheckDto(String name, boolean passed, String message) {}

    public record NearMissDto(
            int totalLosers, int nearMissCount, double nearMissRate, Map<Integer, Integer> distribution) {}
}
