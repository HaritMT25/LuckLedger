package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridPopulator;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Constructive {@link GridPopulator} for Celestial Fortune, the hypergeometric number-match
 * mechanic.
 *
 * <p><strong>Probability model.</strong> A pool of {@value CelestialFortuneEvaluator#NUMBER_POOL_SIZE}
 * numbers (N), {@value CelestialFortuneEvaluator#WINNING_NUMBER_COUNT} winning (n), and
 * {@value CelestialFortuneEvaluator#PLAYER_NUMBER_COUNT} player picks (m). The chance of exactly
 * {@code k} of the player's numbers hitting a winning number is hypergeometric:
 *
 * <pre>{@code  P(k) = C(8,k) * C(22,4-k) / C(30,4)}</pre>
 *
 * <p>Outcomes are predetermined, so this populator reverse-engineers a layout from a target prize
 * rather than sampling one. It maps the prize back to the overlap count {@code k} demanded by the
 * calibrated ladder ({@code $2}=2, {@code $20}=3, {@code $740}=4, {@code $0}=0) and constructs the
 * grid in a single pass — never reject-and-retry:
 *
 * <ul>
 *   <li>draw {@code n} distinct winning numbers from the pool;</li>
 *   <li>draw {@code k} of the player's numbers from the winning set (the guaranteed overlap) and the
 *       remaining {@code m - k} from the complement, so the overlap is exactly {@code k};</li>
 *   <li>fill the inert decoy row from numbers used nowhere else.</li>
 * </ul>
 *
 * <p>A loser ({@code $0}) is built with {@code k = 0}: all player numbers come from the complement,
 * giving zero overlap and therefore no accidental win. The resulting layout follows the contract
 * shared with {@link CelestialFortuneEvaluator} (winning row, two player rows, decoy row), so every
 * grid this produces round-trips through that evaluator to the prize it was built for.
 *
 * <p>Randomness is supplied through a {@link GridUtils} (composition, not inheritance): production
 * wires a {@link java.security.SecureRandom}; tests inject a seeded source for reproducible layouts.
 * Every selection uses {@code GridUtils}' Fisher-Yates draw, so the player cells — and which of them
 * hold the matches — are uniformly shuffled rather than pinned to fixed positions.
 */
public final class CelestialFortunePopulator implements GridPopulator {

    private final GridUtils gridUtils;

    /** Creates a populator backed by a {@link GridUtils} over {@link java.security.SecureRandom}. */
    public CelestialFortunePopulator() {
        this(new GridUtils());
    }

    /**
     * Creates a populator backed by the given utilities.
     *
     * @param gridUtils shared grid helpers carrying the randomness source; never {@code null}
     */
    public CelestialFortunePopulator(GridUtils gridUtils) {
        this.gridUtils = Objects.requireNonNull(gridUtils, "gridUtils must not be null");
    }

    /**
     * Builds a Celestial Fortune grid whose winning/player overlap encodes exactly {@code
     * prizeAmount}.
     *
     * @param size       must be {@link CelestialFortuneEvaluator#GRID_SIZE} ({@link GridSize#FOUR})
     * @param prizeAmount the predetermined prize; one of the calibrated ladder values
     *                    ({@code 0}, {@code 2}, {@code 20}, {@code 740})
     * @param symbolPool the numbers to draw from; must hold at least
     *                   {@value CelestialFortuneEvaluator#NUMBER_POOL_SIZE} distinct symbols
     * @return a fully populated {@link GridSize#FOUR} grid
     * @throws NullPointerException     if {@code size} or {@code symbolPool} is {@code null}
     * @throws IllegalArgumentException if {@code size} is not {@code FOUR}, {@code prizeAmount} is not
     *                                  a ladder value, or {@code symbolPool} lacks enough distinct
     *                                  numbers
     */
    @Override
    public Grid populate(GridSize size, double prizeAmount, List<String> symbolPool) {
        Objects.requireNonNull(size, "size must not be null");
        Objects.requireNonNull(symbolPool, "symbolPool must not be null");
        if (size != CelestialFortuneEvaluator.GRID_SIZE) {
            throw new IllegalArgumentException(
                    "Celestial Fortune requires a " + CelestialFortuneEvaluator.GRID_SIZE
                            + " grid, was " + size);
        }
        if (distinctCount(symbolPool) < CelestialFortuneEvaluator.NUMBER_POOL_SIZE) {
            throw new IllegalArgumentException(
                    "Celestial Fortune needs at least " + CelestialFortuneEvaluator.NUMBER_POOL_SIZE
                            + " distinct numbers, pool had " + distinctCount(symbolPool));
        }

        int overlap = overlapForPrize(prizeAmount);

        List<String> winning =
                gridUtils.getRandomSymbols(
                        symbolPool, CelestialFortuneEvaluator.WINNING_NUMBER_COUNT, Set.of());
        Set<String> winningSet = new HashSet<>(winning);

        List<String> player = new ArrayList<>(CelestialFortuneEvaluator.PLAYER_NUMBER_COUNT);
        player.addAll(gridUtils.getRandomSymbols(winning, overlap, Set.of()));
        player.addAll(
                gridUtils.getRandomSymbols(
                        symbolPool,
                        CelestialFortuneEvaluator.PLAYER_NUMBER_COUNT - overlap,
                        winningSet));
        // Shuffle so the overlapping numbers are not pinned to the first cells.
        player =
                gridUtils.getRandomSymbols(
                        player, CelestialFortuneEvaluator.PLAYER_NUMBER_COUNT, Set.of());

        Set<String> used = new HashSet<>(winningSet);
        used.addAll(player);
        int dimension = size.dimension();
        List<String> decoy = gridUtils.getRandomSymbols(symbolPool, dimension, used);

        return assemble(size, winning, player, decoy);
    }

    private static Grid assemble(
            GridSize size, List<String> winning, List<String> player, List<String> decoy) {
        int dimension = size.dimension();
        Cell[][] cells = new Cell[dimension][dimension];
        for (int col = 0; col < dimension; col++) {
            cells[CelestialFortuneEvaluator.WINNING_ROW][col] =
                    cell(CelestialFortuneEvaluator.WINNING_ROW, col, winning.get(col));
        }
        int p = 0;
        for (int row = CelestialFortuneEvaluator.PLAYER_FIRST_ROW;
                row <= CelestialFortuneEvaluator.PLAYER_LAST_ROW;
                row++) {
            for (int col = 0; col < dimension; col++) {
                cells[row][col] = cell(row, col, player.get(p++));
            }
        }
        for (int col = 0; col < dimension; col++) {
            cells[CelestialFortuneEvaluator.DECOY_ROW][col] =
                    cell(CelestialFortuneEvaluator.DECOY_ROW, col, decoy.get(col));
        }
        return new Grid(size, cells);
    }

    private static Cell cell(int row, int col, String number) {
        return new Cell(new Position(row, col), number, 0.0);
    }

    /**
     * Maps a prize back to the overlap count {@code k} that produces it under the calibrated ladder,
     * choosing the smallest {@code k} for the ambiguous {@code $0} tier (0 or 1 match) so that a
     * loser carries zero overlap and cannot accidentally win.
     */
    private static int overlapForPrize(double prizeAmount) {
        BigDecimal prize = BigDecimal.valueOf(prizeAmount);
        for (int k = 0; k <= CelestialFortuneEvaluator.WINNING_NUMBER_COUNT; k++) {
            if (CelestialFortuneEvaluator.prizeForMatches(k).compareTo(prize) == 0) {
                return k;
            }
        }
        throw new IllegalArgumentException(
                "prizeAmount " + prizeAmount + " is not a Celestial Fortune prize tier");
    }

    private static int distinctCount(List<String> symbolPool) {
        Set<String> distinct = new HashSet<>();
        for (String symbol : symbolPool) {
            if (symbol != null) {
                distinct.add(symbol);
            }
        }
        return distinct.size();
    }
}
