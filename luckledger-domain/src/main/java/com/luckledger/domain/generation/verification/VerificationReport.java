package com.luckledger.domain.generation.verification;

import java.util.List;
import java.util.Objects;

/**
 * The aggregate result of running the verification suite over a generated pool: an overall verdict
 * plus the individual {@link CheckResult}s that produced it. Generation is rejected outright if the
 * report does not pass (the "verification mandatory" invariant) — a failing report is never patched.
 *
 * <p>The {@code passed} flag is required to be consistent with the checks: it is {@code true} exactly
 * when every check passed. Build one with {@link #from(List)} to derive the flag automatically.
 *
 * @param passed the overall verdict; {@code true} iff every check passed
 * @param checks the individual check results; non-null, non-empty, held as an unmodifiable copy
 */
public record VerificationReport(boolean passed, List<CheckResult> checks) {

    public VerificationReport {
        Objects.requireNonNull(checks, "checks must not be null");
        if (checks.isEmpty()) {
            throw new IllegalArgumentException("checks must not be empty");
        }
        checks.forEach(check -> Objects.requireNonNull(check, "checks must not contain null elements"));
        checks = List.copyOf(checks);
        boolean allPassed = checks.stream().allMatch(CheckResult::passed);
        if (passed != allPassed) {
            throw new IllegalArgumentException(
                    "passed (" + passed + ") is inconsistent with the checks (allPassed=" + allPassed + ")");
        }
    }

    /** Builds a report whose verdict is derived from the checks: passing iff every check passed. */
    public static VerificationReport from(List<CheckResult> checks) {
        Objects.requireNonNull(checks, "checks must not be null");
        boolean allPassed = !checks.isEmpty() && checks.stream().allMatch(CheckResult::passed);
        return new VerificationReport(allPassed, checks);
    }

    /** Convenience alias for {@link #passed()}. */
    public boolean allPassed() {
        return passed;
    }
}
