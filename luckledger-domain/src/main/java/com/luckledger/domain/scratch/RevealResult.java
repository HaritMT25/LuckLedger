package com.luckledger.domain.scratch;

import com.luckledger.domain.generation.theme.ThemedGrid;
import com.luckledger.domain.mechanic.EvaluationResult;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of scratching a ticket (the reveal half of the flow): the themed grid the player sees
 * and the evaluation of it. The {@code isWinner} flag and {@code prizeAmount} mirror the
 * {@link EvaluationResult} — they are surfaced here for convenience and must be consistent with it.
 *
 * @param ticketId the revealed ticket; never {@code null}
 * @param skinnedGrid the themed grid shown to the player; never {@code null}
 * @param evaluationResult the mechanic's evaluation of the grid; never {@code null}
 * @param prizeAmount the prize won; never {@code null}, {@code >= 0}, equal to the evaluation's prize
 * @param isWinner whether the ticket won; must equal the evaluation's winner flag
 */
public record RevealResult(
        UUID ticketId,
        ThemedGrid skinnedGrid,
        EvaluationResult evaluationResult,
        BigDecimal prizeAmount,
        boolean isWinner) {

    public RevealResult {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(skinnedGrid, "skinnedGrid must not be null");
        Objects.requireNonNull(evaluationResult, "evaluationResult must not be null");
        Objects.requireNonNull(prizeAmount, "prizeAmount must not be null");
        if (prizeAmount.signum() < 0) {
            throw new IllegalArgumentException("prizeAmount must be >= 0, was " + prizeAmount.toPlainString());
        }
        if (isWinner != evaluationResult.isWinner()) {
            throw new IllegalArgumentException(
                    "isWinner (" + isWinner + ") must match the evaluation (" + evaluationResult.isWinner() + ")");
        }
        if (prizeAmount.compareTo(evaluationResult.prizeAmount()) != 0) {
            throw new IllegalArgumentException(
                    "prizeAmount must equal the evaluation's prize " + evaluationResult.prizeAmount().toPlainString());
        }
    }
}
