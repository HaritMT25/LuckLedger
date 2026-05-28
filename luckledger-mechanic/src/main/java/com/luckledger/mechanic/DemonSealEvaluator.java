package com.luckledger.mechanic;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Demon Seal — the trinomial-reveal mechanic.
 *
 * <p>A ticket reveals six seals, each independently gold, silver, or broken. The seals score
 * {@code T = 2·gold + 1·silver + 0·broken}, so {@code T} ranges over {@code 0..12}. The
 * distribution of {@code T} is the trinomial of six draws with
 * {@code P(gold)=0.12, P(silver)=0.40, P(broken)=0.48}, and the prize ladder below is calibrated to
 * a 64.4% RTP on a $5 ticket (top prize {@code T=12} at roughly 1 in 335,000):
 *
 * <pre>
 *   T 0-3  -> $0      (Escapes)
 *   T 4    -> $2      (Consolation)
 *   T 5    -> $4      (Sealed-S)
 *   T 6    -> $10     (Sealed-M)
 *   T 7    -> $25     (Sealed-L)
 *   T 8    -> $100    (Killed-S)
 *   T 9    -> $300    (Killed-M)
 *   T 10-11-> $2,500  (Killed-E)
 *   T 12   -> $25,000 (Killed-L)
 * </pre>
 *
 * <p>Evaluation is by symbol, not by position: every {@link #GOLD_SEAL}, {@link #SILVER_SEAL}, and
 * {@link #BROKEN_SEAL} cell anywhere in the grid is treated as a seal and any other symbol is
 * ignored. This keeps the evaluator independent of the grid's square shape — a Demon Seal layout
 * carries its six seals plus filler cells. The instance is stateless and may be reused for both
 * generation-time verification and play-time reveal (see {@link WinEvaluator}).
 */
public final class DemonSealEvaluator implements WinEvaluator {

    public static final String GOLD_SEAL = "GOLD";
    public static final String SILVER_SEAL = "SILVER";
    public static final String BROKEN_SEAL = "BROKEN";

    /** Number of seals revealed per Demon Seal ticket. */
    public static final int SEAL_COUNT = 6;

    public static final int GOLD_POINTS = 2;
    public static final int SILVER_POINTS = 1;

    private static final BigDecimal CONSOLATION = new BigDecimal("2"); // T = 4
    private static final BigDecimal SEALED_S = new BigDecimal("4"); // T = 5
    private static final BigDecimal SEALED_M = new BigDecimal("10"); // T = 6
    private static final BigDecimal SEALED_L = new BigDecimal("25"); // T = 7
    private static final BigDecimal KILLED_S = new BigDecimal("100"); // T = 8
    private static final BigDecimal KILLED_M = new BigDecimal("300"); // T = 9
    private static final BigDecimal KILLED_E = new BigDecimal("2500"); // T = 10, 11
    private static final BigDecimal KILLED_L = new BigDecimal("25000"); // T = 12

    /**
     * {@inheritDoc}
     *
     * <p>Counts gold, silver, and broken seals, scores them to {@code T = 2·gold + silver}, and maps
     * {@code T} to its calibrated prize via {@link #prizeForPoints(int)}. A result is a winner when
     * its prize is positive ({@code T >= 4}); winners report the scoring (gold and silver) positions
     * in row-major order, and every result reports per-symbol seal counts in {@code matchDetails}.
     */
    @Override
    public EvaluationResult evaluate(Grid grid) {
        Objects.requireNonNull(grid, "grid must not be null");

        int gold = 0;
        int silver = 0;
        int broken = 0;
        List<Position> scoringPositions = new ArrayList<>();
        for (Cell cell : grid.getAllCells()) {
            switch (cell.symbol()) {
                case GOLD_SEAL -> {
                    gold++;
                    scoringPositions.add(cell.position());
                }
                case SILVER_SEAL -> {
                    silver++;
                    scoringPositions.add(cell.position());
                }
                case BROKEN_SEAL -> broken++;
                default -> { /* filler / non-seal cell — contributes nothing */ }
            }
        }

        int totalPoints = GOLD_POINTS * gold + SILVER_POINTS * silver;
        BigDecimal prize = prizeForPoints(totalPoints);
        boolean winner = prize.signum() > 0;

        Map<String, Integer> matchDetails = new LinkedHashMap<>();
        matchDetails.put(GOLD_SEAL, gold);
        matchDetails.put(SILVER_SEAL, silver);
        matchDetails.put(BROKEN_SEAL, broken);

        List<Position> winningPositions = winner ? scoringPositions : List.of();
        return new EvaluationResult(winner, prize, winningPositions, matchDetails);
    }

    /**
     * Maps the seal score {@code T = 2·gold + silver} to its calibrated prize.
     *
     * @param totalPoints the seal score; must be {@code >= 0} (a valid six-seal layout yields
     *     {@code 0..12})
     * @return the prize for that score; {@link BigDecimal#ZERO} for a non-winning score
     * @throws IllegalArgumentException if {@code totalPoints} is negative
     */
    public static BigDecimal prizeForPoints(int totalPoints) {
        if (totalPoints < 0) {
            throw new IllegalArgumentException("totalPoints must be >= 0, was " + totalPoints);
        }
        return switch (totalPoints) {
            case 0, 1, 2, 3 -> BigDecimal.ZERO;
            case 4 -> CONSOLATION;
            case 5 -> SEALED_S;
            case 6 -> SEALED_M;
            case 7 -> SEALED_L;
            case 8 -> KILLED_S;
            case 9 -> KILLED_M;
            case 10, 11 -> KILLED_E;
            default -> KILLED_L; // T == 12 (and defensively any higher score)
        };
    }
}
