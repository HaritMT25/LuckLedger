package com.luckledger.domain.generation;

import com.luckledger.domain.generation.verification.VerificationReport;

/**
 * Unchecked exception thrown when a generated batch fails verification. Raised by the generation
 * pipeline to abort the <em>entire</em> batch — a failed pool is never patched or partially shipped
 * (the "verification mandatory" invariant). Carries the failing {@link VerificationReport} when one
 * is available so callers can surface exactly which checks failed.
 */
public class GenerationIntegrityException extends RuntimeException {

    private final transient VerificationReport report;

    /**
     * Creates an exception with a message and no attached report.
     *
     * @param message the failure description
     */
    public GenerationIntegrityException(String message) {
        super(message);
        this.report = null;
    }

    /**
     * Creates an exception carrying the verification report that failed.
     *
     * @param message the failure description
     * @param report the failing verification report; may be {@code null}
     */
    public GenerationIntegrityException(String message, VerificationReport report) {
        super(message);
        this.report = report;
    }

    /**
     * Returns the failing verification report, if one was attached.
     *
     * @return the report, or {@code null} when none was supplied
     */
    public VerificationReport getReport() {
        return report;
    }
}
