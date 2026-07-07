package com.luckledger.generation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.luckledger.domain.generation.GenerationResult;
import com.luckledger.domain.generation.NearMissMode;
import com.luckledger.domain.generation.OutcomeGenerator;
import com.luckledger.domain.generation.ShuffleService;
import com.luckledger.domain.generation.theme.AssetRef;
import com.luckledger.domain.generation.theme.CoatingConfig;
import com.luckledger.domain.generation.theme.ColorPalette;
import com.luckledger.domain.generation.theme.ThemeRef;
import com.luckledger.domain.generation.theme.ThemedSymbol;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.pool.BookProfile;
import com.luckledger.domain.pool.PoolContract;
import com.luckledger.domain.pool.PoolValidator;
import com.luckledger.domain.pool.PrizeTier;
import com.luckledger.generation.theme.ThemeSkinningService;
import com.luckledger.generation.verification.VerificationSuite;
import com.luckledger.mechanic.DemonSealMechanic;
import com.luckledger.mechanic.NearMissAnalyzer;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pipeline-level tests for RTP-neutral near-miss engineering.
 *
 * <p>Follows the Monte-Carlo style of the generation suite: it generates a full, seed-sized pool
 * twice — once {@link NearMissMode#CLEAN}, once {@link NearMissMode#REALISTIC} — and proves that the
 * only thing REALISTIC changes is the <em>shape</em> of losing grids. Under REALISTIC exactly
 * {@code round(0.35 * loserCount)} losers are engineered into near-misses; the tier counts, every
 * prize, the summed payout, and the payout ratio are byte-for-byte the same as CLEAN, and the
 * mandatory verification gate still passes.
 */
class GenerationPipelineNearMissTest {

    private static final DemonSealMechanic MECHANIC = new DemonSealMechanic();

    /** A theme mapping every symbol the Demon Seal populator can place. */
    private static ThemeRef demonTheme() {
        Map<String, ThemedSymbol> symbols = new LinkedHashMap<>();
        for (String s : MECHANIC.getDefaultSymbolPool()) {
            symbols.put(s, new ThemedSymbol(s, "🔮", null, s));
        }
        return new ThemeRef(
                "demon",
                "Demon Seal",
                symbols,
                new ColorPalette("#1", "#2", "#3", "#4", "#5"),
                new AssetRef("/bg.png"),
                new CoatingConfig("#C4A535", List.of("#1", "#2"), 0.6, 45, 5),
                null);
    }

    private static GenerationPipeline pipeline() {
        return new GenerationPipeline(
                new OutcomeGenerator(),
                new ShuffleService(),
                MECHANIC,
                new ThemeSkinningService(List.of(demonTheme())),
                new VerificationSuite(new PoolValidator(), new NearMissAnalyzer()),
                GridSize.THREE);
    }

    /**
     * A loser-rich pool: 500 tickets, 75 winners, 425 losers; tier cost $460 on $2,500 revenue, so
     * payoutRatio = 0.184. Deliberately many losers so the engineered near-miss count is large.
     */
    private static PoolContract loserRichPool() {
        return PoolContract.builder()
                .totalTickets(500)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.184")) // 460 tier cost / 2500 revenue
                .addPrizeTier(new PrizeTier(new BigDecimal("100"), 1, "Killed-S"))
                .addPrizeTier(new PrizeTier(new BigDecimal("25"), 4, "Sealed-L"))
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 10, "Sealed-M"))
                .addPrizeTier(new PrizeTier(new BigDecimal("4"), 20, "Sealed-S"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 40, "Consolation"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .build();
    }

    private static BigDecimal totalPayout(GenerationResult result) {
        return result.tickets().stream()
                .map(card -> card.layout().prizeAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void realisticEngineersTheDesignRateOfNearMissesWithoutMovingRtp() {
        PoolContract pool = loserRichPool();
        GenerationPipeline pipeline = pipeline();

        GenerationResult clean =
                pipeline.generate(pool, MechanicType.DEMON_SEAL, demonTheme(), NearMissMode.CLEAN);
        GenerationResult realistic =
                pipeline.generate(pool, MechanicType.DEMON_SEAL, demonTheme(), NearMissMode.REALISTIC);

        int loserCount = pool.getLoserCount();
        int expectedEngineered =
                (int) Math.round(GenerationPipeline.ENGINEERED_NEAR_MISS_RATE * loserCount);
        assertThat(expectedEngineered).as("the design rate must engineer some near-misses").isPositive();

        // Both batches ship: the mandatory verification gate is applied unchanged and passes.
        assertThat(clean.verificationReport().passed()).isTrue();
        assertThat(realistic.verificationReport().passed()).isTrue();

        // Same loser population in both modes.
        assertThat(clean.nearMissReport().totalLosers()).isEqualTo(loserCount);
        assertThat(realistic.nearMissReport().totalLosers()).isEqualTo(loserCount);

        // CLEAN engineers nothing; REALISTIC engineers exactly round(0.35 * loserCount).
        assertThat(clean.nearMissReport().nearMissCount()).isZero();
        assertThat(realistic.nearMissReport().nearMissCount()).isEqualTo(expectedEngineered);
        assertThat(realistic.nearMissReport().nearMissRate())
                .isCloseTo(GenerationPipeline.ENGINEERED_NEAR_MISS_RATE, within(0.02));

        // RTP is sacred: identical summed payout (hence identical payout ratio) in both modes, and
        // that payout equals the pool's tier cost exactly.
        assertThat(totalPayout(realistic))
                .as("total payout must not change between CLEAN and REALISTIC")
                .isEqualByComparingTo(totalPayout(clean));
        assertThat(totalPayout(realistic)).isEqualByComparingTo(pool.getTierCost());
        assertThat(realistic.tickets()).hasSameSizeAs(clean.tickets());
    }

    @Test
    void cleanModeShipsNoEngineeredNearMisses() {
        GenerationResult clean = pipeline()
                .generate(loserRichPool(), MechanicType.DEMON_SEAL, demonTheme(), NearMissMode.CLEAN);

        assertThat(clean.verificationReport().passed()).isTrue();
        assertThat(clean.nearMissReport().nearMissCount()).isZero();
        assertThat(clean.nearMissReport().nearMissRate()).isZero();
    }

    @Test
    void theThreeArgOverloadDefaultsToCleanMode() {
        PoolContract pool = loserRichPool();

        GenerationResult viaOverload = pipeline().generate(pool, MechanicType.DEMON_SEAL, demonTheme());

        assertThat(viaOverload.nearMissReport().nearMissCount()).isZero();
        assertThat(viaOverload.verificationReport().passed()).isTrue();
    }
}
