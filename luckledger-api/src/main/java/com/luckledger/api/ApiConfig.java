package com.luckledger.api;

import com.luckledger.cli.GameOrchestrator;
import com.luckledger.distribution.BookPartitioner;
import com.luckledger.distribution.DealerAllocator;
import com.luckledger.distribution.DealerRegistry;
import com.luckledger.distribution.DealerTierResolver;
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
import com.luckledger.domain.pool.PrizeTier;
import com.luckledger.generation.pipeline.GenerationPipeline;
import com.luckledger.generation.theme.ThemeSkinningService;
import com.luckledger.generation.verification.VerificationSuite;
import com.luckledger.mechanic.CelestialFortuneMechanic;
import com.luckledger.mechanic.DemonSealMechanic;
import com.luckledger.mechanic.GameMechanic;
import com.luckledger.domain.pool.PoolValidator;
import com.luckledger.mechanic.NearMissAnalyzer;
import com.luckledger.domain.ledger.InevitabilityCurveInsight;
import com.luckledger.domain.ledger.InsightGenerator;
import com.luckledger.domain.ledger.LossChasingInsight;
import com.luckledger.domain.ledger.LossRateInsight;
import com.luckledger.domain.ledger.LuckyStoreDebunkInsight;
import com.luckledger.domain.ledger.NearMissInsight;
import com.luckledger.domain.ledger.VarianceExplanationInsight;
import com.luckledger.player.bank.BankService;
import com.luckledger.player.ledger.LedgerService;
import com.luckledger.player.ledger.TransactionRecorder;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the pure-domain services (which carry no Spring annotations) into Spring beans and assembles
 * the two pre-generated games' orchestrators. The state engine is entirely in-memory; there is no
 * datastore.
 */
@Configuration
public class ApiConfig {

    static final String CELESTIAL_THEME_ID = "celestial";
    static final String DEMON_THEME_ID = "demon";

    private static final GameMechanic CELESTIAL = new CelestialFortuneMechanic();
    private static final GameMechanic DEMON = new DemonSealMechanic();

    @Bean
    public PoolValidator poolValidator() {
        return new PoolValidator();
    }

    @Bean
    public NearMissAnalyzer nearMissAnalyzer() {
        return new NearMissAnalyzer();
    }

    @Bean
    public OutcomeGenerator outcomeGenerator() {
        return new OutcomeGenerator();
    }

    @Bean
    public ShuffleService shuffleService() {
        return new ShuffleService();
    }

    @Bean
    public BookPartitioner bookPartitioner() {
        return new BookPartitioner();
    }

    @Bean
    public DealerTierResolver dealerTierResolver() {
        return new DealerTierResolver();
    }

    @Bean
    public VerificationSuite verificationSuite(PoolValidator validator, NearMissAnalyzer analyzer) {
        return new VerificationSuite(validator, analyzer);
    }

    /** The single append-only ledger shared across bank, purchase, and reveal — Postgres-backed. */
    @Bean
    public TransactionRecorder transactionRecorder(
            com.luckledger.api.persistence.TransactionRepository transactionRepository) {
        return new com.luckledger.api.persistence.JpaTransactionRecorder(transactionRepository);
    }

    @Bean
    public BankService bankService(TransactionRecorder transactionRecorder) {
        return new BankService(transactionRecorder);
    }

    @Bean
    public LedgerService ledgerService(TransactionRecorder transactionRecorder) {
        Clock clock = Clock.systemUTC();
        List<InsightGenerator> generators = List.of(
                new LossRateInsight(clock),
                new LossChasingInsight(clock),
                new LuckyStoreDebunkInsight(clock),
                new NearMissInsight(clock),
                new VarianceExplanationInsight(clock),
                new InevitabilityCurveInsight(clock));
        return new LedgerService(transactionRecorder, generators);
    }

    @Bean
    public ThemeSkinningService themeSkinningService() {
        return new ThemeSkinningService(List.of(
                theme(CELESTIAL_THEME_ID, "Celestial Fortune", CELESTIAL.getDefaultSymbolPool()),
                theme(DEMON_THEME_ID, "Demon Seal", DEMON.getDefaultSymbolPool())));
    }

    @Bean
    public GameOrchestrator celestialOrchestrator(
            OutcomeGenerator outcomeGenerator,
            ShuffleService shuffleService,
            ThemeSkinningService themes,
            VerificationSuite verificationSuite,
            BookPartitioner bookPartitioner,
            DealerTierResolver tierResolver) {
        GenerationPipeline pipeline = new GenerationPipeline(
                outcomeGenerator, shuffleService, CELESTIAL, themes, verificationSuite, GridSize.FOUR);
        return new GameOrchestrator(
                pipeline, bookPartitioner, new DealerAllocator(tierResolver), new DealerRegistry(50), themes);
    }

    @Bean
    public GameOrchestrator demonOrchestrator(
            OutcomeGenerator outcomeGenerator,
            ShuffleService shuffleService,
            ThemeSkinningService themes,
            VerificationSuite verificationSuite,
            BookPartitioner bookPartitioner,
            DealerTierResolver tierResolver) {
        GenerationPipeline pipeline = new GenerationPipeline(
                outcomeGenerator, shuffleService, DEMON, themes, verificationSuite, GridSize.THREE);
        return new GameOrchestrator(
                pipeline, bookPartitioner, new DealerAllocator(tierResolver), new DealerRegistry(50), themes);
    }

    // --- seed configs ---------------------------------------------------------

    /** Celestial Fortune demo pool: 200 tickets, prizes $2/$20/$740 (the calibrated CF ladder). */
    static GameConfig celestialConfig() {
        PoolContract pool = PoolContract.builder()
                .totalTickets(200)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.88")) // 880 tier cost / 1000 revenue
                .addPrizeTier(new PrizeTier(new BigDecimal("740"), 1, "Jackpot"))
                .addPrizeTier(new PrizeTier(new BigDecimal("20"), 5, "Mid"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 20, "Consolation"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .build();
        return new GameConfig(pool, MechanicType.CELESTIAL_FORTUNE, CELESTIAL_THEME_ID, 10, 5);
    }

    /** Demon Seal demo pool: 20 tickets, prizes $2/$10. */
    static GameConfig demonConfig() {
        PoolContract pool = PoolContract.builder()
                .totalTickets(20)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.30")) // 30 tier cost / 100 revenue
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 2, "Sealed-M"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 5, "Consolation"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .build();
        return new GameConfig(pool, MechanicType.DEMON_SEAL, DEMON_THEME_ID, 4, 3);
    }

    private static ThemeRef theme(String id, String name, List<String> symbols) {
        Map<String, ThemedSymbol> symbolMap = new LinkedHashMap<>();
        for (String symbol : symbols) {
            symbolMap.put(symbol, new ThemedSymbol(symbol, "🎴", null, symbol));
        }
        return new ThemeRef(
                id,
                name,
                symbolMap,
                new ColorPalette("#1A0F00", "#8B6914", "#FFD700", "#1A0F00", "#E8D5B0"),
                new AssetRef("/assets/themes/" + id + "/background.png"),
                new CoatingConfig("#C4A535", List.of("#8B6914", "#C4A535", "#DAB94A"), 0.6, 45, 5),
                new AssetRef("/assets/effects/sparkle.gif"));
    }
}
