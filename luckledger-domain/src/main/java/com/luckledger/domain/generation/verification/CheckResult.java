package com.luckledger.domain.generation.verification;

import java.util.Objects;

/**
 * The outcome of a single verification check run against a generated pool — one per check (tier
 * counts, payout ratio, no false positives, ...). The verification suite collects these into a
 * report; generation is rejected if any check fails (the "verification mandatory" invariant).
 *
 * @param name a short, non-blank identifier for the check (e.g. {@code "Payout Ratio"})
 * @param passed whether the check held
 * @param message a human-readable detail; empty for a passing check with nothing to report
 */
public record CheckResult(String name, boolean passed, String message) {

    public CheckResult {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /** A passing check with no further detail. */
    public static CheckResult passed(String name) {
        return new CheckResult(name, true, "");
    }

    /** A failing check carrying the reason it failed. */
    public static CheckResult failed(String name, String message) {
        return new CheckResult(name, false, message);
    }
}
