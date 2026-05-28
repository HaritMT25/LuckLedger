package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates a Celestial Fortune (number-match) grid and awards a prize based on how many of the
 * player's numbers match the winning numbers.
 *
 * <p><strong>Probability model — hypergeometric.</strong> A pool of {@value #NUMBER_POOL_SIZE}
 * numbers, {@value #WINNING_NUMBER_COUNT} of them winning, and {@value #PLAYER_NUMBER_COUNT} player
 * picks. The chance of exactly {@code k} matches is:
 *
 * <pre>{@code  P(k) = C(8,k) * C(22,4-k) / C(30,4)}</pre>
 *
 * <p>The prize ladder is calibrated to a 65.2% return-to-player on a $5 ticket:
 *
 * <table>
 *   <caption>Prize by overlap count</caption>
 *   <tr><th>matches k</th><th>P(k)</th><th>prize</th></tr>
 *   <tr><td>0</td><td>0.2669</td><td>$0</td></tr>
 *   <tr><td>1</td><td>0.4496</td><td>$0</td></tr>
 *   <tr><td>2</td><td>0.2360</td><td>$2</td></tr>
 *   <tr><td>3</td><td>0.0450</td><td>$20</td></tr>
 *   <tr><td>4</td><td>0.00255</td><td>$740</td></tr>
 * </table>
 *
 * <p><strong>Grid layout contract.</strong> The shared layout that this evaluator and the
 * {@code CelestialFortunePopulator} agree on, encoded in a {@link GridSize#FOUR} grid:
 *
 * <ul>
 *   <li>row {@value #WINNING_ROW} (4 cells) — the winning numbers</li>
 *   <li>rows {@value #PLAYER_FIRST_ROW}-{@value #PLAYER_LAST_ROW} (8 cells, row-major) — the
 *       player's numbers</li>
 *   <li>row {@value #DECOY_ROW} (4 cells) — inert decoy; the asymmetric 4-plus-8 number-match
 *       layout does not tile a square grid, so the final row is reserved padding and is never read
 *       during evaluation</li>
 * </ul>
 *
 * <p>Each cell's {@link Cell#symbol()} carries the number; {@link Cell#prizeValue()} is unused (the
 * prize is a function of the overlap count, not of any single cell).
 *
 * <p>This evaluator is stateless and may be reused across grids, as required by {@link WinEvaluator}.
 */
public final class CelestialFortuneEvaluator implements WinEvaluator {

    /** Size of the number pool the winning and player numbers are drawn from (N). */
    public static final int NUMBER_POOL_SIZE = 30;

    /** Count of winning numbers drawn (n). */
    public static final int WINNING_NUMBER_COUNT = 4;

    /** Count of numbers the player picks (m). */
    public static final int PLAYER_NUMBER_COUNT = 8;

    /** Grid size this mechanic is laid out on. */
    public static final GridSize GRID_SIZE = GridSize.FOUR;

    /** Row holding the winning numbers. */
    public static final int WINNING_ROW = 0;

    /** First row holding player numbers (inclusive). */
    public static final int PLAYER_FIRST_ROW = 1;

    /** Last row holding player numbers (inclusive). */
    public static final int PLAYER_LAST_ROW = 2;

    /** Reserved decoy row, ignored during evaluation. */
    public static final int DECOY_ROW = 3;

    /**
     * Key under which the overlap count {@code k} is published in
     * {@link EvaluationResult#matchDetails()}. A single aggregate entry is used (rather than one
     * entry per matched number) so that {@code max(matchDetails.values())} equals {@code k}, which
     * is what the {@code NearMissAnalyzer} compares against the win threshold of 2.
     */
    public static final String MATCH_COUNT_KEY = "matchCount";

    private static final BigDecimal PRIZE_TWO_MATCHES = new BigDecimal("2");
    private static final BigDecimal PRIZE_THREE_MATCHES = new BigDecimal("20");
    private static final BigDecimal PRIZE_FOUR_MATCHES = new BigDecimal("740");

    /**
     * Maps an overlap count to its prize amount under the calibrated ladder.
     *
     * @param matches number of player numbers that hit a winning number, in
     *     {@code [0, WINNING_NUMBER_COUNT]}
     * @return the prize: {@code $0} for 0-1 matches, {@code $2}/{@code $20}/{@code $740} for 2/3/4
     * @throws IllegalArgumentException if {@code matches} is outside {@code [0, 4]} (an overlap of
     *     more than 4 is impossible with only 4 winning numbers)
     */
    public static BigDecimal prizeForMatches(int matches) {
        if (matches < 0 || matches > WINNING_NUMBER_COUNT) {
            throw new IllegalArgumentException(
                    "matches must be in [0, " + WINNING_NUMBER_COUNT + "], was " + matches);
        }
        return switch (matches) {
            case 2 -> PRIZE_TWO_MATCHES;
            case 3 -> PRIZE_THREE_MATCHES;
            case 4 -> PRIZE_FOUR_MATCHES;
            default -> BigDecimal.ZERO;
        };
    }

    @Override
    public EvaluationResult evaluate(Grid grid) {
        Objects.requireNonNull(grid, "grid must not be null");
        if (grid.size() != GRID_SIZE) {
            throw new IllegalArgumentException(
                    "Celestial Fortune requires a " + GRID_SIZE + " grid, was " + grid.size());
        }

        Set<String> winningNumbers = winningNumbers(grid);

        List<Position> matchedPositions = new ArrayList<>();
        Set<String> matchedNumbers = new HashSet<>();
        for (int row = PLAYER_FIRST_ROW; row <= PLAYER_LAST_ROW; row++) {
            for (int col = 0; col < GRID_SIZE.dimension(); col++) {
                Cell cell = grid.getCell(row, col);
                if (winningNumbers.contains(cell.symbol())) {
                    matchedPositions.add(cell.position());
                    matchedNumbers.add(cell.symbol());
                }
            }
        }

        int matches = matchedNumbers.size();
        BigDecimal prize = prizeForMatches(matches);
        boolean winner = prize.signum() > 0;
        List<Position> winningPositions = winner ? List.copyOf(matchedPositions) : List.of();

        return new EvaluationResult(winner, prize, winningPositions, Map.of(MATCH_COUNT_KEY, matches));
    }

    private static Set<String> winningNumbers(Grid grid) {
        Set<String> winning = new HashSet<>();
        for (int col = 0; col < GRID_SIZE.dimension(); col++) {
            winning.add(grid.getCell(WINNING_ROW, col).symbol());
        }
        return winning;
    }
}
