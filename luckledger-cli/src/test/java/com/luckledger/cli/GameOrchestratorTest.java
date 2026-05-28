package com.luckledger.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.distribution.BookPartitioner;
import com.luckledger.distribution.DealerAllocator;
import com.luckledger.distribution.DealerRegistry;
import com.luckledger.distribution.DealerTierResolver;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.generation.OutcomeGenerator;
import com.luckledger.domain.generation.ShuffleService;
import com.luckledger.domain.generation.theme.AssetRef;
import com.luckledger.domain.generation.theme.CoatingConfig;
import com.luckledger.domain.generation.theme.ColorPalette;
import com.luckledger.domain.generation.theme.ThemeRef;
import com.luckledger.domain.generation.theme.ThemedSymbol;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.orchestration.GameConfig;
import com.luckledger.domain.pool.BookProfile;
import com.luckledger.domain.pool.PoolContract;
import com.luckledger.domain.pool.PoolValidator;
import com.luckledger.domain.pool.PrizeTier;
import com.luckledger.generation.pipeline.GenerationPipeline;
import com.luckledger.generation.theme.ThemeSkinningService;
import com.luckledger.generation.verification.VerificationSuite;
import com.luckledger.mechanic.DemonSealMechanic;
import com.luckledger.mechanic.NearMissAnalyzer;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GameOrchestratorTest {

    private static final DemonSealMechanic MECHANIC = new DemonSealMechanic();

    private static ThemeRef demonTheme() {
        Map<String, ThemedSymbol> symbols = new LinkedHashMap<>();
        for (String s : MECHANIC.getDefaultSymbolPool()) {
            symbols.put(s, new ThemedSymbol(s, "🔮", null, s));
        }
        return new ThemeRef(
                "demon", "Demon Seal", symbols,
                new ColorPalette("#1", "#2", "#3", "#4", "#5"),
                new AssetRef("/bg.png"),
                new CoatingConfig("#1", List.of("#1"), 0.5, 0, 1),
                null);
    }

    private static final ThemeSkinningService THEMES = new ThemeSkinningService(List.of(demonTheme()));

    private static GameOrchestrator orchestrator() {
        GenerationPipeline pipeline = new GenerationPipeline(
                new OutcomeGenerator(),
                new ShuffleService(),
                MECHANIC,
                THEMES,
                new VerificationSuite(new PoolValidator(), new NearMissAnalyzer()),
                GridSize.THREE);
        return new GameOrchestrator(
                pipeline,
                new BookPartitioner(),
                new DealerAllocator(new DealerTierResolver(), new Random(7L)),
                new DealerRegistry(5),
                THEMES);
    }

    // 20 tickets, 2x$10 + 5x$2 + 13 losers; payoutRatio 30/100 = 0.30
    private static GameConfig config() {
        PoolContract pool = PoolContract.builder()
                .totalTickets(20)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.30"))
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 2, "Sealed-M"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 5, "Consolation"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .build();
        return new GameConfig(pool, MechanicType.DEMON_SEAL, "demon", 4, 3);
    }

    @Test
    void setupGeneratesPartitionsAndAllocatesAFullGame() {
        GameSetupResult result = orchestrator().setup(config());

        assertThat(result.dealers()).hasSize(3);
        assertThat(result.generationResult().verificationReport().passed()).isTrue();
        assertThat(result.generationResult().tickets()).hasSize(20);
        assertThat(result.partitionResult().books()).hasSize(4);
        assertThat(result.allocationMap().keySet()).containsExactlyInAnyOrderElementsOf(result.dealers());
        // fresh dealers all start TIER_1, so only the LOWER band is allocatable; the rest overflow
        int allocated = result.allocationMap().values().stream().mapToInt(List::size).sum();
        assertThat(allocated).isBetween(1, 4);
    }

    @Test
    void restockCycleReusesExistingDealers() {
        GameOrchestrator orchestrator = orchestrator();
        orchestrator.setup(config()); // creates 3 dealers in the registry

        GameSetupResult restock = orchestrator.restockCycle(config());

        assertThat(restock.dealers()).hasSize(3); // same dealers, not a fresh set
        assertThat(restock.generationResult().tickets()).hasSize(20);
    }

    @Test
    void nullConfigIsRejected() {
        assertThatThrownBy(() -> orchestrator().setup(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void anUnknownThemeIsRejected() {
        GameConfig badTheme = new GameConfig(
                config().poolContract(), MechanicType.DEMON_SEAL, "nonexistent", 4, 3);

        assertThatThrownBy(() -> orchestrator().setup(badTheme))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
