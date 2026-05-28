package com.luckledger.domain.generation;

import com.luckledger.domain.generation.verification.NearMissReport;
import com.luckledger.domain.generation.verification.VerificationReport;
import java.util.List;
import java.util.Objects;

/**
 * The final product of a generation run: the renderable tickets plus the evidence that the batch is
 * sound. A {@code GenerationResult} only exists for a batch that passed verification (the
 * "verification mandatory" invariant); the reports are retained for auditing and insights.
 *
 * @param tickets the generated, renderable tickets; non-null, held as an unmodifiable copy
 * @param verificationReport the mandatory verification outcome; non-null
 * @param nearMissReport the informational near-miss summary; non-null
 * @param generationTimeMs wall-clock time the run took, in milliseconds; {@code >= 0}
 */
public record GenerationResult(
        List<TicketCard> tickets,
        VerificationReport verificationReport,
        NearMissReport nearMissReport,
        long generationTimeMs) {

    public GenerationResult {
        Objects.requireNonNull(tickets, "tickets must not be null");
        Objects.requireNonNull(verificationReport, "verificationReport must not be null");
        Objects.requireNonNull(nearMissReport, "nearMissReport must not be null");
        if (generationTimeMs < 0) {
            throw new IllegalArgumentException("generationTimeMs must be >= 0, was " + generationTimeMs);
        }
        tickets.forEach(ticket -> Objects.requireNonNull(ticket, "tickets must not contain null elements"));
        tickets = List.copyOf(tickets);
    }
}
