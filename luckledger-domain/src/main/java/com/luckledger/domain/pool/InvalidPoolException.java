package com.luckledger.domain.pool;

import java.util.List;

/**
 * Unchecked exception thrown when a pool fails its constraint validation.
 *
 * <p>Raised by the pool factory when a {@code PoolContract} cannot be constructed because
 * it violates one or more validation rules. Carries the collected error messages so callers
 * can surface every problem at once.
 */
public class InvalidPoolException extends RuntimeException {

    private final List<String> errors;

    /**
     * Creates an exception with a message and no detailed errors.
     *
     * @param message the failure description
     */
    public InvalidPoolException(String message) {
        super(message);
        this.errors = List.of();
    }

    /**
     * Creates an exception with a message and a list of underlying validation errors.
     *
     * @param message the failure description
     * @param errors  the individual validation error messages; may be null (treated as empty)
     */
    public InvalidPoolException(String message, List<String> errors) {
        super(message);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Creates an exception from a failing {@link ValidationResult}.
     *
     * @param message the failure description
     * @param result  the validation result whose errors are captured
     */
    public InvalidPoolException(String message, ValidationResult result) {
        this(message, result == null ? List.of() : result.errors());
    }

    /**
     * Returns the underlying validation errors.
     *
     * @return an immutable list of error messages, empty when none were supplied
     */
    public List<String> getErrors() {
        return errors;
    }
}
