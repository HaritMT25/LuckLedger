package com.luckledger.cli;

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
import com.luckledger.domain.pool.PoolValidator;
import com.luckledger.domain.pool.PrizeTier;
import com.luckledger.generation.pipeline.GenerationPipeline;
import com.luckledger.generation.theme.ThemeSkinningService;
import com.luckledger.generation.verification.VerificationSuite;
import com.luckledger.mechanic.CelestialFortuneMechanic;
import com.luckledger.mechanic.DemonSealMechanic;
import com.luckledger.mechanic.GameMechanic;
import com.luckledger.mechanic.NearMissAnalyzer;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the generation CLI. Parses a few options (mechanic, book/dealer counts), wires the
 * generation subsystems for the chosen mechanic, and runs {@link GenerateCommand}. Output is via
 * SLF4J (see {@code GenerateCommand}).
 *
 * <p>Usage: {@code --mechanic DEMON_SEAL|CELESTIAL_FORTUNE --books N --dealers N}. The pool is a
 * built-in demo pool per mechanic (full pool-param parsing is out of scope for this demo CLI).
 */
public final class CliApp {

    private CliApp() {}

    public static void main(String[] args) {
        Options options = Options.parse(args);
        GameMechanic mechanic = mechanic(options.mechanic());
        GridSize gridSize = options.mechanic() == MechanicType.CELESTIAL_FORTUNE ? GridSize.FOUR : GridSize.THREE;
        ThemeRef theme = defaultTheme(mechanic);

        GameOrchestrator orchestrator = buildOrchestrator(mechanic, gridSize, theme);
        GameConfig config = new GameConfig(
                defaultPool(options.mechanic()), options.mechanic(), theme.themeId(), options.books(), options.dealers());
        new GenerateCommand(orchestrator).generate(config);
    }

    static GameOrchestrator buildOrchestrator(GameMechanic mechanic, GridSize gridSize, ThemeRef theme) {
        ThemeSkinningService themes = new ThemeSkinningService(List.of(theme));
        GenerationPipeline pipeline = new GenerationPipeline(
                new OutcomeGenerator(),
                new ShuffleService(),
                mechanic,
                themes,
                new VerificationSuite(new PoolValidator(), new NearMissAnalyzer()),
                gridSize);
        return new GameOrchestrator(
                pipeline, new BookPartitioner(), new DealerAllocator(new DealerTierResolver()), new DealerRegistry(50), themes);
    }

    static GameMechanic mechanic(MechanicType type) {
        return type == MechanicType.CELESTIAL_FORTUNE ? new CelestialFortuneMechanic() : new DemonSealMechanic();
    }

    static ThemeRef defaultTheme(GameMechanic mechanic) {
        Map<String, ThemedSymbol> symbolMap = new LinkedHashMap<>();
        for (String symbol : mechanic.getDefaultSymbolPool()) {
            symbolMap.put(symbol, new ThemedSymbol(symbol, "🎴", null, symbol));
        }
        String id = mechanic.getType().name().toLowerCase();
        return new ThemeRef(
                id,
                mechanic.getType().name(),
                symbolMap,
                new ColorPalette("#1A0F00", "#8B6914", "#FFD700", "#1A0F00", "#E8D5B0"),
                new AssetRef("/assets/themes/" + id + "/background.png"),
                new CoatingConfig("#C4A535", List.of("#8B6914", "#C4A535"), 0.6, 45, 5),
                null);
    }

    static PoolContract defaultPool(MechanicType type) {
        if (type == MechanicType.CELESTIAL_FORTUNE) {
            return PoolContract.builder()
                    .totalTickets(200)
                    .ticketPrice(new BigDecimal("5"))
                    .payoutRatio(new BigDecimal("0.88"))
                    .addPrizeTier(new PrizeTier(new BigDecimal("740"), 1, "Jackpot"))
                    .addPrizeTier(new PrizeTier(new BigDecimal("20"), 5, "Mid"))
                    .addPrizeTier(new PrizeTier(new BigDecimal("2"), 20, "Consolation"))
                    .minPayout(BigDecimal.ZERO)
                    .bookProfile(BookProfile.BALANCED)
                    .build();
        }
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

    /** Parsed CLI options with demo defaults. */
    record Options(MechanicType mechanic, int books, int dealers) {

        static Options parse(String[] args) {
            MechanicType mechanic = MechanicType.DEMON_SEAL;
            int books = 4;
            int dealers = 3;
            for (int i = 0; i + 1 < args.length; i += 2) {
                switch (args[i]) {
                    case "--mechanic" -> mechanic = MechanicType.valueOf(args[i + 1]);
                    case "--books" -> books = Integer.parseInt(args[i + 1]);
                    case "--dealers" -> dealers = Integer.parseInt(args[i + 1]);
                    default -> { /* ignore unknown flags */ }
                }
            }
            return new Options(mechanic, books, dealers);
        }
    }
}
