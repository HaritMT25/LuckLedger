package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridPopulator;
import com.luckledger.domain.mechanic.GridSize;
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
 *   <li>picks a point total {@code T} whose ladder prize equals the requested amount (the ambiguous
 *       tiers — {@code $0} spanning {@code T 0..3} and {@code $2,500} spanning {@code T 10..11} —
 *       are resolved by a uniform random draw, for layout variety);
 *   <li>enumerates the seal compositions {@code (g, s, b)} with {@code 2g + s = T} and
 *       {@code g + s + b = 6}, and picks one uniformly at random;
 *   <li>lays those six seals at six Fisher-Yates–shuffled grid positions and fills every remaining
 *       cell with a non-seal filler symbol (the evaluator scores by symbol and ignores filler, so a
 *       square {@link Grid} simply carries six seals plus filler).
 * </ol>
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

    /** Seal symbols — must match {@code DemonSealEvaluator} so a built layout scores as intended. */
    public static final String GOLD_SEAL = "GOLD";

    public static final String SILVER_SEAL = "SILVER";
    public static final String BROKEN_SEAL = "BROKEN";

    /** Number of seals on every Demon Seal ticket. */
    public static final int SEAL_COUNT = 6;

    public static final int GOLD_POINTS = 2;
    public static final int SILVER_POINTS = 1;

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
        Objects.requireNonNull(size, "size must not be null");
        Objects.requireNonNull(symbolPool, "symbolPool must not be null");

        int dimension = size.dimension();
        int cellCount = dimension * dimension;
        if (cellCount < SEAL_COUNT) {
            throw new IllegalArgumentException(
                    "grid of size " + dimension + " holds " + cellCount + " cells, need at least "
                            + SEAL_COUNT + " for Demon Seal");
        }

        BigDecimal targetPrize = toLadderPrize(prizeAmount);
        int targetPoints = chooseTargetPoints(targetPrize);
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

    /** Picks a point total whose ladder prize equals {@code targetPrize}, uniformly at random. */
    private int chooseTargetPoints(BigDecimal targetPrize) {
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
