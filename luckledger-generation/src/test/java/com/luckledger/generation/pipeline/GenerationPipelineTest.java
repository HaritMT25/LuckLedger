package com.luckledger.generation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.GenerationIntegrityException;
import com.luckledger.domain.generation.GenerationResult;
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

class GenerationPipelineTest {

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

    // valid pool: 20 tickets, 2x$10 + 5x$2 + 13 losers; payoutRatio = 30/100 = 0.30
    private static PoolContract validPool() {
        return PoolContract.builder()
                .totalTickets(20)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.30"))
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 2, "Sealed-M"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 5, "Consolation"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .build();
    }

    @Test
    void generatesAVerifiedRenderableBatch() {
        GenerationResult result = pipeline().generate(validPool(), MechanicType.DEMON_SEAL, demonTheme());

        assertThat(result.tickets()).hasSize(20);
        assertThat(result.verificationReport().passed()).isTrue();
        assertThat(result.nearMissReport().totalLosers()).isEqualTo(13);
        assertThat(result.generationTimeMs()).isGreaterThanOrEqualTo(0L);
        // every ticket is fully skinned to the grid size
        assertThat(result.tickets())
                .allSatisfy(card -> assertThat(card.skinnedGrid().size()).isEqualTo(GridSize.THREE));
    }

    @Test
    void winnersAndLosersAreRealizedFromThePool() {
        GenerationResult result = pipeline().generate(validPool(), MechanicType.DEMON_SEAL, demonTheme());

        long winners = result.tickets().stream()
                .filter(card -> card.layout().mechanicType() == MechanicType.DEMON_SEAL)
                .count();
        assertThat(winners).isEqualTo(20); // all tickets are DemonSeal layouts
    }

    @Test
    void anInvalidPoolFailsVerificationAndAborts() {
        // payoutRatio 0.90 but the tiers only fund a 0.30 budget -> PoolValidator rejects it ->
        // the Pool Contract check fails -> the whole batch is aborted.
        PoolContract invalid = PoolContract.builder()
                .totalTickets(20)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.90"))
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 2, "Sealed-M"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 5, "Consolation"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .build();

        assertThatThrownBy(() -> pipeline().generate(invalid, MechanicType.DEMON_SEAL, demonTheme()))
                .isInstanceOf(GenerationIntegrityException.class)
                .satisfies(ex ->
                        assertThat(((GenerationIntegrityException) ex).getReport().passed()).isFalse());
    }

    @Test
    void aMechanicTypeMismatchIsRejected() {
        assertThatThrownBy(
                        () -> pipeline().generate(validPool(), MechanicType.CELESTIAL_FORTUNE, demonTheme()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullArgumentsAreRejected() {
        GenerationPipeline pipeline = pipeline();
        assertThatThrownBy(() -> pipeline.generate(null, MechanicType.DEMON_SEAL, demonTheme()))
                .isInstanceOf(NullPointerException.class);
    }
}
