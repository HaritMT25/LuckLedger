package com.luckledger.api;

import com.luckledger.api.OutcomeNarrative.CellRef;
import com.luckledger.api.persistence.GridCodec;
import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.mechanic.NearMissResult;
import com.luckledger.mechanic.CelestialFortuneEvaluator;
import com.luckledger.mechanic.DemonSealEvaluator;
import com.luckledger.mechanic.NearMissAnalyzer;
import com.luckledger.mechanic.WinEvaluator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Produces the read-only {@link OutcomeNarrative} that explains an already-revealed ticket. It rebuilds
 * the ticket's stored mechanic grid ({@link GridCodec#toDomain}), re-evaluates it with the mechanic's
 * evaluator, and asks the {@link NearMissAnalyzer} whether a loser was engineered close to a win.
 *
 * <p><strong>Payout is sacred.</strong> This class re-evaluates the grid <em>only</em> to describe it.
 * If the freshly evaluated prize disagrees with the ticket's persisted {@code prizeAmount} (the amount
 * the reveal actually credits), that is a data-integrity anomaly: the narrator logs an ERROR with both
 * amounts and returns {@code null}, emitting no narrative. It never changes any credit, debit, or
 * ledger amount — the reveal path always follows the stored prize.
 */
@Component
class RevealNarrator {

    private static final Logger log = LoggerFactory.getLogger(RevealNarrator.class);

    /** Celestial Fortune pays from two matches; a loser at one match is one short. */
    private static final int CELESTIAL_WIN_FLOOR = 2;

    /** Demon Seal seals a win at {@code T = 4}; a loser at {@code T = 3} is one short. */
    private static final int DEMON_WIN_FLOOR = 4;

    private final NearMissAnalyzer nearMissAnalyzer;

    RevealNarrator(NearMissAnalyzer nearMissAnalyzer) {
        this.nearMissAnalyzer = nearMissAnalyzer;
    }

    /**
     * Builds a narrative for a revealed ticket, or {@code null} when it cannot (missing inputs, an
     * unsupported mechanic, or the sacred-payout guard tripping).
     *
     * @param gridDto     the ticket's stored mechanic grid; {@code null} yields a {@code null} narrative
     * @param type        the mechanic that produced the grid
     * @param storedPrize the ticket's persisted prize — the authoritative payout amount
     * @param ticketId    the ticket id, used only for logging a guard trip
     * @return the narrative, or {@code null}
     */
    OutcomeNarrative narrate(GridCodec.GridDto gridDto, MechanicType type, BigDecimal storedPrize, UUID ticketId) {
        if (gridDto == null || type == null || storedPrize == null) {
            return null;
        }
        WinEvaluator evaluator = evaluatorFor(type);
        if (evaluator == null) {
            return null; // no narrative model for this mechanic yet
        }

        Grid grid = GridCodec.toDomain(gridDto);
        EvaluationResult result = evaluator.evaluate(grid);

        // Sacred-payout guard: the reveal already credited the stored prize. If a re-evaluation of the
        // stored grid disagrees, refuse to narrate rather than describe a payout that never happened.
        if (result.prizeAmount().compareTo(storedPrize) != 0) {
            log.error(
                    "Reveal narrative guard tripped for ticket {}: re-evaluated prize {} disagrees with stored "
                            + "prize {}. Suppressing narrative; the payout path is untouched and credits the stored prize.",
                    ticketId, result.prizeAmount().toPlainString(), storedPrize.toPlainString());
            return null;
        }

        NearMissResult nearMiss = nearMissAnalyzer.analyze(result, type);
        return switch (type) {
            case CELESTIAL_FORTUNE -> celestial(grid, result, nearMiss, storedPrize);
            case DEMON_SEAL -> demon(grid, result, nearMiss, storedPrize);
            default -> null;
        };
    }

    private static WinEvaluator evaluatorFor(MechanicType type) {
        return switch (type) {
            case CELESTIAL_FORTUNE -> new CelestialFortuneEvaluator();
            case DEMON_SEAL -> new DemonSealEvaluator();
            default -> null;
        };
    }

    private OutcomeNarrative celestial(
            Grid grid, EvaluationResult result, NearMissResult nearMiss, BigDecimal prize) {
        int matchCount = result.matchDetails().getOrDefault(CelestialFortuneEvaluator.MATCH_COUNT_KEY, 0);
        List<CellRef> matched = celestialMatchedCells(grid);
        int winningNumbers = CelestialFortuneEvaluator.WINNING_NUMBER_COUNT;

        String summary;
        if (result.isWinner()) {
            summary = "Matched %d of the %d winning numbers — %s coins.".formatted(matchCount, winningNumbers, coins(prize));
        } else if (nearMiss.isNearMiss()) {
            summary = "Matched %d of the %d winning numbers — one short of a prize. That near-miss is by design."
                    .formatted(matchCount, winningNumbers);
        } else {
            summary = "None of your numbers matched the winning row.";
        }
        return new OutcomeNarrative(
                matchCount, CELESTIAL_WIN_FLOOR, null, null, null, null, nearMiss.isNearMiss(), summary, matched);
    }

    private OutcomeNarrative demon(
            Grid grid, EvaluationResult result, NearMissResult nearMiss, BigDecimal prize) {
        int gold = result.matchDetails().getOrDefault(DemonSealEvaluator.GOLD_SEAL, 0);
        int silver = result.matchDetails().getOrDefault(DemonSealEvaluator.SILVER_SEAL, 0);
        int score = DemonSealEvaluator.GOLD_POINTS * gold + DemonSealEvaluator.SILVER_POINTS * silver;
        List<CellRef> scoring = demonScoringCells(grid);

        String summary;
        if (result.isWinner()) {
            summary = "%d seal points (%d gold, %d silver) — %s coins. The demon is sealed."
                    .formatted(score, gold, silver, coins(prize));
        } else if (nearMiss.isNearMiss()) {
            summary = "%d seal points — one short of the %d-point floor. That near-miss is by design."
                    .formatted(score, DEMON_WIN_FLOOR);
        } else {
            summary = "%d seal points — you needed %d to seal the demon.".formatted(score, DEMON_WIN_FLOOR);
        }
        return new OutcomeNarrative(
                null, null, score, gold, silver, DEMON_WIN_FLOOR, nearMiss.isNearMiss(), summary, scoring);
    }

    /** The player cells whose number hits the winning row — the cells that "counted". */
    private static List<CellRef> celestialMatchedCells(Grid grid) {
        int dimension = grid.size().dimension();
        Set<String> winning = new HashSet<>();
        for (int col = 0; col < dimension; col++) {
            winning.add(grid.getCell(CelestialFortuneEvaluator.WINNING_ROW, col).symbol());
        }
        List<CellRef> matched = new ArrayList<>();
        for (int row = CelestialFortuneEvaluator.PLAYER_FIRST_ROW;
                row <= CelestialFortuneEvaluator.PLAYER_LAST_ROW;
                row++) {
            for (int col = 0; col < dimension; col++) {
                if (winning.contains(grid.getCell(row, col).symbol())) {
                    matched.add(new CellRef(row, col));
                }
            }
        }
        return matched;
    }

    /** The gold and silver seal cells — the scoring seals worth highlighting. */
    private static List<CellRef> demonScoringCells(Grid grid) {
        List<CellRef> scoring = new ArrayList<>();
        for (Cell cell : grid.getAllCells()) {
            if (DemonSealEvaluator.GOLD_SEAL.equals(cell.symbol())
                    || DemonSealEvaluator.SILVER_SEAL.equals(cell.symbol())) {
                scoring.add(new CellRef(cell.position().row(), cell.position().col()));
            }
        }
        return scoring;
    }

    /** Formats a whole-coin prize amount for a summary sentence (drops trailing scale zeros). */
    private static String coins(BigDecimal prize) {
        return prize.stripTrailingZeros().toPlainString();
    }
}
