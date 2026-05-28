package com.luckledger.domain.pool;

import java.util.List;
import java.util.Objects;

/**
 * Immutable pass/fail result of validating a pool, carrying any error messages.
 *
 * <p>A valid result carries no errors; an invalid result carries one or more. The
 * validity is derived from whether the error list is empty, keeping the two states
 * internally consistent.
 */
public final class ValidationResult {

    private final List<String> errors;

    private ValidationResult(List<String> errors) {
        this.errors = List.copyOf(errors);
    }

    /**
     * Creates a passing result with no errors.
     *
     * @return a valid {@link ValidationResult}
     */
    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    /**
     * Creates a failing result from a list of error messages.
     *
     * <p>The supplied list is defensively copied; subsequent mutation of the source
     * does not affect this result.
     *
     * @param errors the error messages; must not be null and must contain at least one entry
     * @return an invalid {@link ValidationResult}
     * @throws NullPointerException     if {@code errors} is null
     * @throws IllegalArgumentException if {@code errors} is empty
     */
    public static ValidationResult invalid(List<String> errors) {
        Objects.requireNonNull(errors, "errors must not be null");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("an invalid result must carry at least one error");
        }
        return new ValidationResult(errors);
    }

    /**
     * Creates a failing result from one or more error messages.
     *
     * @param errors the error messages; must contain at least one entry
     * @return an invalid {@link ValidationResult}
     * @throws IllegalArgumentException if no errors are supplied
     */
    public static ValidationResult invalid(String... errors) {
        Objects.requireNonNull(errors, "errors must not be null");
        return invalid(List.of(errors));
    }

    /**
     * Reports whether validation passed.
     *
     * @return {@code true} if there are no errors, otherwise {@code false}
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Returns the error messages.
     *
     * @return an immutable list of error messages, empty when the result is valid
     */
    public List<String> errors() {
        return errors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ValidationResult other)) {
            return false;
        }
        return errors.equals(other.errors);
    }

    @Override
    public int hashCode() {
        return errors.hashCode();
    }

    @Override
    public String toString() {
        return "ValidationResult{valid=" + isValid() + ", errors=" + errors + '}';
    }
}
