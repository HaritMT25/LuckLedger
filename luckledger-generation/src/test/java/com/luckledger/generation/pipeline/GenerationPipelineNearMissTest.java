package com.luckledger.generation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.luckledger.domain.generation.GenerationIntegrityException;
import com.luckledger.domain.generation.GenerationResult;
import com.luckledger.domain.generation.NearMissMode;
import com.luckledger.domain.generation.OutcomeGenerator;
import com.luckledger.domain.generation.ShuffleService;
import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.generation.theme.AssetRef;
import com.luckledger.domain.generation.theme.CoatingConfig;
import com.luckledger.domain.generation.theme.ColorPalette;
import com.luckledger.domain.generation.theme.ThemeRef;
import com.luckledger.domain.generation.theme.ThemedSymbol;
import com.luckledger.domain.mechanic.EvaluationResult;
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
import com.luckledger.mechanic.WinEvaluator;
import java.math.BigDecimal;
import java.util.ArrayList;
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

    /**
     * The engineered near-misses must be spread across the <em>entire</em> shipped sale order, not
     * piled into its prefix. {@link com.luckledger.distribution.BookPartitioner} deals the shuffled
     * list as contiguous book-sized chunks and never re-shuffles, so a prefix selection would hand the
     * front books every near-miss and the back books none. This partitions the batch into the
     * book-sized chunks a 20-book deal produces and asserts near-misses appear throughout: every book
     * gets at least one, no book has <em>all</em> of its losers engineered, and both the first and last
     * third carry near-misses. It fails on the old "first {@code round(0.35 * loserCount)} losers"
     * behavior, whose near-misses all land in the earliest tickets (leaving the last third — and most
     * books — with zero).
     */
    @Test
    void realisticSpreadsEngineeredNearMissesAcrossTheWholeSaleOrder() {
        PoolContract pool = loserRichPool();
        GenerationResult realistic =
                pipeline().generate(pool, MechanicType.DEMON_SEAL, demonTheme(), NearMissMode.REALISTIC);

        List<TicketCard> tickets = realistic.tickets();
        List<Boolean> nearMiss = nearMissFlags(realistic);
        int total = tickets.size();
        int expectedEngineered =
                (int) Math.round(GenerationPipeline.ENGINEERED_NEAR_MISS_RATE * zeroPrizeLoserCount(realistic));
        assertThat(expectedEngineered).as("this pool must engineer a large, spreadable count").isGreaterThan(20);

        // Aggregate count is unchanged by spreading, and every engineered near-miss sits on a $0
        // ticket (only $0 tickets are eligible — the Finding-2 predicate).
        assertThat(countTrue(nearMiss)).isEqualTo(expectedEngineered);
        for (int i = 0; i < total; i++) {
            if (nearMiss.get(i)) {
                assertThat(tickets.get(i).layout().prizeAmount())
                        .as("engineered near-miss at index %d must be a $0 ticket", i)
                        .isEqualByComparingTo("0");
            }
        }

        // Partition into the contiguous book-sized chunks BookPartitioner would deal (20 books of 25).
        int bookCount = 20;
        int bookSize = total / bookCount;
        int booksWithNoNearMiss = 0;
        int booksWithEveryLoserEngineered = 0;
        for (int book = 0; book < bookCount; book++) {
            int from = book * bookSize;
            int nmInBook = 0;
            int losersInBook = 0;
            for (int i = from; i < from + bookSize; i++) {
                if (tickets.get(i).layout().prizeAmount().signum() == 0) {
                    losersInBook++;
                }
                if (nearMiss.get(i)) {
                    nmInBook++;
                }
            }
            if (nmInBook == 0) {
                booksWithNoNearMiss++;
            }
            if (losersInBook > 0 && nmInBook == losersInBook) {
                booksWithEveryLoserEngineered++;
            }
        }
        // Old prefix behavior: the front books are 100% engineered losers while the back books hold
        // zero. A uniform spread shows neither extreme in any book.
        assertThat(booksWithNoNearMiss)
                .as("no book may be dealt zero engineered near-misses (old prefix code left ~13 empty)")
                .isZero();
        assertThat(booksWithEveryLoserEngineered)
                .as("no book may have every one of its losers engineered (old prefix code did to the front)")
                .isZero();

        // Near-misses reach both ends of the sale order (the old prefix code left the last third empty).
        assertThat(countTrue(nearMiss.subList(0, total / 3)))
                .as("first third of the sale order carries near-misses")
                .isPositive();
        assertThat(countTrue(nearMiss.subList(total - total / 3, total)))
                .as("last third of the sale order carries near-misses")
                .isPositive();
    }

    /**
     * RTP guard: CLEAN and REALISTIC ship the identical multiset of prize amounts — the same count at
     * every winning tier and the same loser count — and therefore the identical summed payout. Only the
     * shape of $0 grids differs between the two modes.
     */
    @Test
    void cleanAndRealisticShipIdenticalTierCountsAndPayout() {
        PoolContract pool = loserRichPool();
        GenerationPipeline pipeline = pipeline();

        GenerationResult clean =
                pipeline.generate(pool, MechanicType.DEMON_SEAL, demonTheme(), NearMissMode.CLEAN);
        GenerationResult realistic =
                pipeline.generate(pool, MechanicType.DEMON_SEAL, demonTheme(), NearMissMode.REALISTIC);

        for (PrizeTier tier : pool.prizeTiers()) {
            assertThat(countAtPrize(realistic, tier.value()))
                    .as("tier $%s count", tier.value().toPlainString())
                    .isEqualTo(countAtPrize(clean, tier.value()))
                    .isEqualTo((long) tier.count());
        }
        assertThat(countAtPrize(realistic, BigDecimal.ZERO))
                .as("loser count")
                .isEqualTo(countAtPrize(clean, BigDecimal.ZERO))
                .isEqualTo((long) pool.getLoserCount());
        assertThat(totalPayout(realistic))
                .as("summed payout (hence RTP) is identical across modes")
                .isEqualByComparingTo(totalPayout(clean));
    }

    /**
     * Finding 2: only $0 tickets are eligible for engineered near-misses. A pool with a positive
     * minimum-payout floor has none — its "losers" pay the floor, which the mechanic scores as a win —
     * so REALISTIC has nothing to engineer and behaves exactly like CLEAN. Because a floor "loser" pays
     * money, the mandatory No-False-Positives gate rejects the batch, and it does so identically in
     * both modes: REALISTIC can never silently ship a mis-engineered positive-floor pool, and the
     * pipeline never spends its quota flagging floor tickets the populators would ignore.
     */
    @Test
    void positiveFloorPoolIsHandledIdenticallyInBothModesAndNeverSilentlyEngineered() {
        PoolContract floorPool = positiveFloorPool();
        GenerationPipeline pipeline = pipeline();

        // The pool is economically valid and its "losers" pay the positive floor (there are no $0 tickets).
        assertThat(new PoolValidator().validate(floorPool).isValid()).isTrue();
        assertThat(floorPool.getLoserCount()).isPositive();
        assertThat(floorPool.minPayout()).isEqualByComparingTo("2");

        assertThatThrownBy(() ->
                        pipeline.generate(floorPool, MechanicType.DEMON_SEAL, demonTheme(), NearMissMode.CLEAN))
                .as("a positive-floor pool is rejected by the mandatory gate regardless of mode")
                .isInstanceOf(GenerationIntegrityException.class);
        assertThatThrownBy(() ->
                        pipeline.generate(floorPool, MechanicType.DEMON_SEAL, demonTheme(), NearMissMode.REALISTIC))
                .as("REALISTIC does not change the outcome for a positive-floor pool")
                .isInstanceOf(GenerationIntegrityException.class);
    }

    /**
     * Edge case: when the design rate rounds to zero — a pool with a single $0 loser, since
     * {@code round(0.35 * 1) == 0} — the pipeline engineers no near-misses and still verifies. Guards
     * the spread selector's {@code N == 0} boundary.
     */
    @Test
    void aSingleZeroLoserEngineersNothingWhenTheRateRoundsToZero() {
        // 100 tickets: $2 x 99 winners + 1 $0 loser; tierCost 198 = revenue 500 x 0.396.
        PoolContract almostAllWinners = PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.396"))
                .addPrizeTier(new PrizeTier(new BigDecimal("2"), 99, "Consolation"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .build();

        int zeroLosers = almostAllWinners.getLoserCount();
        assertThat(zeroLosers).isEqualTo(1);
        assertThat((int) Math.round(GenerationPipeline.ENGINEERED_NEAR_MISS_RATE * zeroLosers))
                .as("the rate must round to zero for a single loser")
                .isZero();

        GenerationResult realistic = pipeline()
                .generate(almostAllWinners, MechanicType.DEMON_SEAL, demonTheme(), NearMissMode.REALISTIC);

        assertThat(realistic.verificationReport().passed()).isTrue();
        assertThat(realistic.nearMissReport().nearMissCount()).isZero();
    }

    /**
     * A positive-floor pool: 100 tickets, minPayout $2, winners $10x4 + $4x10 (14 winners) and 86 floor
     * "losers" at $2. tierCost 80 + floorCost 172 = 252 = revenue 500 x payoutRatio 0.504. Valid, but
     * every floor ticket pays $2 (a win), which is why the verification gate rejects the batch.
     */
    private static PoolContract positiveFloorPool() {
        return PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.504"))
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 4, "Sealed-M"))
                .addPrizeTier(new PrizeTier(new BigDecimal("4"), 10, "Sealed-S"))
                .minPayout(new BigDecimal("2"))
                .bookProfile(BookProfile.BALANCED)
                .build();
    }

    /**
     * Flags each ticket, in shipped (sale) order, as an engineered near-miss — a losing grid the
     * {@link NearMissAnalyzer} judges exactly one step short of a win. Mirrors how the pipeline's
     * {@link NearMissReport} is computed, so the count here equals the reported near-miss count.
     */
    private static List<Boolean> nearMissFlags(GenerationResult result) {
        WinEvaluator evaluator = MECHANIC.createEvaluator();
        NearMissAnalyzer analyzer = new NearMissAnalyzer();
        List<Boolean> flags = new ArrayList<>(result.tickets().size());
        for (TicketCard card : result.tickets()) {
            EvaluationResult evaluation = evaluator.evaluate(card.layout().grid());
            boolean nearMiss = !evaluation.isWinner()
                    && analyzer.analyze(evaluation, MechanicType.DEMON_SEAL).isNearMiss();
            flags.add(nearMiss);
        }
        return flags;
    }

    private static int zeroPrizeLoserCount(GenerationResult result) {
        return (int) result.tickets().stream()
                .filter(card -> card.layout().prizeAmount().signum() == 0)
                .count();
    }

    private static long countAtPrize(GenerationResult result, BigDecimal prize) {
        return result.tickets().stream()
                .filter(card -> card.layout().prizeAmount().compareTo(prize) == 0)
                .count();
    }

    private static long countTrue(List<Boolean> flags) {
        return flags.stream().filter(Boolean::booleanValue).count();
    }
}
