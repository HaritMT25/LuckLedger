package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridPopulator;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.LoserLayout;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Demon Seal populator — the constructive inverse of {@code DemonSealEvaluator}.
 *
 * <p>A Demon Seal ticket reveals six seals, each gold, silver, or broken, scoring
 * {@code T = 2·gold + 1·silver + 0·broken} over {@code 0..12}. The evaluator maps {@code T} to a
 * calibrated prize ladder; this populator runs that map backwards. Given a predetermined prize it:
 *
 * <ol>
 *   <li>picks a point total {@code T} whose ladder prize equals the requested amount. The {@code $0}
 *       tier spans {@code T 0..3}, so its {@code T} depends on the requested {@link LoserLayout}:
 *       {@link LoserLayout#CLEAN} draws uniformly from {@code {0,1,2}} (deliberately excluding 3, so
 *       a clean pool carries no accidental near-miss), while {@link LoserLayout#NEAR_MISS} forces
 *       {@code T = 3} — one short of the {@code T = 4} win floor, an engineered near-miss that still
 *       scores {@code $0}. The other ambiguous tier ({@code $2,500} spanning {@code T 10..11}) is
 *       resolved by a uniform draw, for layout variety;
 *   <li>enumerates the seal compositions {@code (g, s, b)} with {@code 2g + s = T} and
 *       {@code g + s + b = 6}, and picks one uniformly at random;
 *   <li>lays those six seals at six Fisher-Yates–shuffled grid positions and fills every remaining
 *       cell with a non-seal filler symbol (the evaluator scores by symbol and ignores filler, so a
 *       square {@link Grid} simply carries six seals plus filler).
 * </ol>
 *
 * <p>Near-miss engineering is RTP-neutral: it only moves a {@code $0} loser's {@code T} between the
 * clean band and the near-miss value, never changing that the ticket pays {@code $0}.
 *
 * <p>The algorithm is fully constructive — a single forward pass with no reject-and-retry — and ends
 * with a self-verification ({@link #verify}) that re-counts the placed seals and confirms they score
 * back to the requested prize. Population never invents a prize: an amount absent from the ladder
 * throws rather than approximating, preserving the "payout ratio is sacred" invariant.
 *
 * <p>Randomness is constructor-injected: production wires a {@link SecureRandom}; tests pass a seeded
 * source for reproducible layouts. The same source backs the internal {@link GridUtils}, so a seed
 * fixes the entire layout.
 *
 * @see GridPopulator
 * @see GridUtils
 */
public final class DemonSealPopulator implements GridPopulator {

    /**
     * Seal symbols and scoring — reused directly from {@link DemonSealEvaluator} so the populator and
     * the evaluator share a single source of truth (a built layout necessarily scores as intended).
     */
    public static final String GOLD_SEAL = DemonSealEvaluator.GOLD_SEAL;

    public static final String SILVER_SEAL = DemonSealEvaluator.SILVER_SEAL;
    public static final String BROKEN_SEAL = DemonSealEvaluator.BROKEN_SEAL;

    /** Number of seals on every Demon Seal ticket. */
    public static final int SEAL_COUNT = DemonSealEvaluator.SEAL_COUNT;

    public static final int GOLD_POINTS = DemonSealEvaluator.GOLD_POINTS;
    public static final int SILVER_POINTS = DemonSealEvaluator.SILVER_POINTS;

    /**
     * Point total for an engineered near-miss loser: one short of the {@code T = 4} win floor. A
     * {@code T = 3} grid still scores {@code $0} but reads as "you almost won".
     */
    static final int NEAR_MISS_POINTS = 3;

    /**
     * Highest point total a {@link LoserLayout#CLEAN} loser may carry. {@code T = 3} is reserved for
     * engineered near-misses, so a clean loser stays in {@code {0,1,2}} and never brushes the win
     * threshold by accident.
     */
    static final int CLEAN_MAX_LOSER_POINTS = 2;

    private static final Set<String> SEAL_SYMBOLS = Set.of(GOLD_SEAL, SILVER_SEAL, BROKEN_SEAL);

    /**
     * Calibrated prize for each point total {@code T = 0..12} (mirrors {@code DemonSealEvaluator}'s
     * ladder). Indexed by {@code T}; the populator inverts this map to choose a {@code T} per prize.
     */
    private static final BigDecimal[] PRIZE_BY_POINTS = {
        BigDecimal.ZERO, // T = 0
        BigDecimal.ZERO, // T = 1
        BigDecimal.ZERO, // T = 2
        BigDecimal.ZERO, // T = 3
        new BigDecimal("2"), // T = 4   Consolation
        new BigDecimal("4"), // T = 5   Sealed-S
        new BigDecimal("10"), // T = 6  Sealed-M
        new BigDecimal("25"), // T = 7  Sealed-L
        new BigDecimal("100"), // T = 8 Killed-S
        new BigDecimal("300"), // T = 9 Killed-M
        new BigDecimal("2500"), // T = 10 Killed-E
        new BigDecimal("2500"), // T = 11 Killed-E
        new BigDecimal("25000"), // T = 12 Killed-L
    };

    private final RandomGenerator random;
    private final GridUtils gridUtils;

    /** Creates a populator backed by {@link SecureRandom} for production use. */
    public DemonSealPopulator() {
        this(new SecureRandom());
    }

    /**
     * Creates a populator backed by the given randomness source.
     *
     * @param random the randomness source, shared with the internal {@link GridUtils}; never
     *     {@code null}
     */
    public DemonSealPopulator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.gridUtils = new GridUtils(random);
    }

    /**
     * {@inheritDoc}
     *
     * @param size the square grid to produce; its cell count must be at least {@link #SEAL_COUNT}
     * @param prizeAmount the predetermined prize; must be a value present on the Demon Seal ladder
     *     ({@code 0, 2, 4, 10, 25, 100, 300, 2500, 25000})
     * @param symbolPool filler symbols for the non-seal cells; must contain at least one symbol that
     *     is not a seal symbol
     * @return a fully populated grid carrying exactly {@link #SEAL_COUNT} seals that score to
     *     {@code prizeAmount}, with every other cell filled from {@code symbolPool}
     * @throws NullPointerException if {@code size} or {@code symbolPool} is {@code null}
     * @throws IllegalArgumentException if {@code prizeAmount} is not on the ladder, the grid is too
     *     small to hold the seals, or {@code symbolPool} offers no non-seal filler
     */
    @Override
    public Grid populate(GridSize size, double prizeAmount, List<String> symbolPool) {
        return populate(size, prizeAmount, symbolPool, LoserLayout.CLEAN);
    }

    /**
     * Builds a Demon Seal grid, additionally choosing how a losing ({@code $0}) grid is shaped.
     *
     * <p>Identical to {@link #populate(GridSize, double, List)} for winners. For a {@code $0} loser,
     * {@link LoserLayout#CLEAN} draws {@code T} from {@code {0,1,2}} while {@link LoserLayout#NEAR_MISS}
     * forces {@code T = }{@value #NEAR_MISS_POINTS} — an engineered near-miss that still scores
     * {@code $0}. This remains one constructive forward pass; the {@code T} is chosen up front and the
     * seals are built to it, never evaluate-and-retry.
     *
     * @param size the square grid to produce; its cell count must be at least {@link #SEAL_COUNT}
     * @param prizeAmount the predetermined prize; must be a value present on the Demon Seal ladder
     * @param symbolPool filler symbols for the non-seal cells; must contain at least one non-seal
     * @param layout how to shape a losing grid; ignored when {@code prizeAmount} is a winner
     * @return a fully populated grid carrying exactly {@link #SEAL_COUNT} seals scoring to
     *     {@code prizeAmount}
     * @throws NullPointerException if {@code size}, {@code symbolPool}, or {@code layout} is
     *     {@code null}
     * @throws IllegalArgumentException if {@code prizeAmount} is not on the ladder, the grid is too
     *     small to hold the seals, or {@code symbolPool} offers no non-seal filler
     */
    @Override
    public Grid populate(
            GridSize size, double prizeAmount, List<String> symbolPool, LoserLayout layout) {
        Objects.requireNonNull(size, "size must not be null");
        Objects.requireNonNull(symbolPool, "symbolPool must not be null");
        Objects.requireNonNull(layout, "layout must not be null");

        int dimension = size.dimension();
        int cellCount = dimension * dimension;
        if (cellCount < SEAL_COUNT) {
            throw new IllegalArgumentException(
                    "grid of size " + dimension + " holds " + cellCount + " cells, need at least "
                            + SEAL_COUNT + " for Demon Seal");
        }

        BigDecimal targetPrize = toLadderPrize(prizeAmount);
        int targetPoints = chooseTargetPoints(targetPrize, layout);
        int[] composition = chooseComposition(targetPoints);

        List<String> seals = buildSeals(composition);
        List<Position> positions = shuffledPositions(dimension);

        Grid grid = placeSeals(emptyGrid(size), seals, positions);
        grid = gridUtils.fillRemaining(grid, symbolPool, SEAL_SYMBOLS);

        verify(grid, targetPoints, targetPrize);
        return grid;
    }

    private static BigDecimal toLadderPrize(double prizeAmount) {
        if (prizeAmount < 0) {
            throw new IllegalArgumentException("prizeAmount must be >= 0, was " + prizeAmount);
        }
        return BigDecimal.valueOf(prizeAmount);
    }

    /**
     * Picks a point total whose ladder prize equals {@code targetPrize}.
     *
     * <p>The {@code $0} tier is special because it spans {@code T = 0..3} and is where near-misses are
     * engineered: {@link LoserLayout#NEAR_MISS} forces {@code T = }{@value #NEAR_MISS_POINTS} (one
     * short of the win floor), while {@link LoserLayout#CLEAN} draws uniformly from
     * {@code 0..}{@value #CLEAN_MAX_LOSER_POINTS}, excluding 3 so a clean loser never sits at the
     * near-miss boundary. Winning prizes ignore {@code layout} and draw uniformly among the point
     * totals that pay them (e.g. {@code $2,500} from {@code {10, 11}}).
     */
    private int chooseTargetPoints(BigDecimal targetPrize, LoserLayout layout) {
        if (targetPrize.signum() == 0) {
            if (layout == LoserLayout.NEAR_MISS) {
                return NEAR_MISS_POINTS;
            }
            return random.nextInt(CLEAN_MAX_LOSER_POINTS + 1);
        }
        List<Integer> candidates = new ArrayList<>();
        for (int points = 0; points < PRIZE_BY_POINTS.length; points++) {
            if (PRIZE_BY_POINTS[points].compareTo(targetPrize) == 0) {
                candidates.add(points);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "prize " + targetPrize.toPlainString() + " is not on the Demon Seal ladder");
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** Picks a seal composition {@code (gold, silver, broken)} for the target points at random. */
    private int[] chooseComposition(int targetPoints) {
        List<int[]> candidates = new ArrayList<>();
        for (int gold = 0; gold <= SEAL_COUNT; gold++) {
            int silver = targetPoints - GOLD_POINTS * gold;
            if (silver < 0 || gold + silver > SEAL_COUNT) {
                continue;
            }
            candidates.add(new int[] {gold, silver, SEAL_COUNT - gold - silver});
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("no seal composition yields T=" + targetPoints);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static List<String> buildSeals(int[] composition) {
        List<String> seals = new ArrayList<>(SEAL_COUNT);
        addRepeated(seals, GOLD_SEAL, composition[0]);
        addRepeated(seals, SILVER_SEAL, composition[1]);
        addRepeated(seals, BROKEN_SEAL, composition[2]);
        return seals;
    }

    private static void addRepeated(List<String> target, String symbol, int times) {
        for (int i = 0; i < times; i++) {
            target.add(symbol);
        }
    }

    /** All grid positions in row-major order, Fisher-Yates shuffled with the injected RNG. */
    private List<Position> shuffledPositions(int dimension) {
        List<Position> positions = new ArrayList<>(dimension * dimension);
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                positions.add(new Position(row, col));
            }
        }
        for (int i = positions.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Position swap = positions.get(i);
            positions.set(i, positions.get(j));
            positions.set(j, swap);
        }
        return positions;
    }

    private static Grid emptyGrid(GridSize size) {
        int dimension = size.dimension();
        Cell[][] cells = new Cell[dimension][dimension];
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                cells[row][col] = new Cell(new Position(row, col), GridUtils.EMPTY, 0.0);
            }
        }
        return new Grid(size, cells);
    }

    /** Places the {@link #SEAL_COUNT} seals onto the first six shuffled positions. */
    private Grid placeSeals(Grid grid, List<String> seals, List<Position> positions) {
        Map<String, List<Position>> bySymbol = new LinkedHashMap<>();
        for (int i = 0; i < SEAL_COUNT; i++) {
            bySymbol.computeIfAbsent(seals.get(i), key -> new ArrayList<>()).add(positions.get(i));
        }
        Grid placed = grid;
        for (Map.Entry<String, List<Position>> entry : bySymbol.entrySet()) {
            placed = gridUtils.placeSymbolsAtPositions(placed, entry.getKey(), entry.getValue());
        }
        return placed;
    }

    /** Re-derives the score from the finished grid and confirms it matches the intended outcome. */
    private static void verify(Grid grid, int expectedPoints, BigDecimal expectedPrize) {
        int gold = grid.getCellsBySymbol(GOLD_SEAL).size();
        int silver = grid.getCellsBySymbol(SILVER_SEAL).size();
        int broken = grid.getCellsBySymbol(BROKEN_SEAL).size();

        int sealTotal = gold + silver + broken;
        if (sealTotal != SEAL_COUNT) {
            throw new IllegalStateException(
                    "expected " + SEAL_COUNT + " seals, built " + sealTotal);
        }
        int points = GOLD_POINTS * gold + SILVER_POINTS * silver;
        if (points != expectedPoints) {
            throw new IllegalStateException(
                    "expected T=" + expectedPoints + ", built T=" + points);
        }
        BigDecimal prize = PRIZE_BY_POINTS[points];
        if (prize.compareTo(expectedPrize) != 0) {
            throw new IllegalStateException(
                    "T=" + points + " scores " + prize.toPlainString() + ", expected "
                            + expectedPrize.toPlainString());
        }
        for (Cell cell : grid.getAllCells()) {
            if (GridUtils.EMPTY.equals(cell.symbol())) {
                throw new IllegalStateException("grid has unfilled cells after population");
            }
        }
    }
}
