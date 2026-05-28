package com.luckledger.api;

import com.luckledger.distribution.BookDepletedException;
import com.luckledger.domain.generation.GenerationIntegrityException;
import com.luckledger.domain.player.InsufficientBalanceException;
import com.luckledger.domain.pool.InvalidPoolException;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into the uniform {@link ErrorResponse} envelope with the appropriate
 * HTTP status, so controllers never have to build error responses themselves.
 *
 * <ul>
 *   <li>{@code 402} — insufficient balance to buy a ticket
 *   <li>{@code 404} — unknown id (player, game, ticket, ...)
 *   <li>{@code 409} — illegal state (e.g. a dealer at capacity)
 *   <li>{@code 410} — the book is depleted
 *   <li>{@code 422} — invalid input (validation / invalid pool)
 *   <li>{@code 500} — generation integrity failure or an unexpected error
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        return build(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_BALANCE", ex);
    }

    @ExceptionHandler(BookDepletedException.class)
    public ResponseEntity<ErrorResponse> handleBookDepleted(BookDepletedException ex) {
        return build(HttpStatus.GONE, "BOOK_DEPLETED", ex);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex);
    }

    @ExceptionHandler(InvalidPoolException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPool(InvalidPoolException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_POOL", ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidation(IllegalArgumentException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", ex);
    }

    @ExceptionHandler(GenerationIntegrityException.class)
    public ResponseEntity<ErrorResponse> handleGenerationIntegrity(GenerationIntegrityException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "GENERATION_FAILED", ex);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, Exception ex) {
        String message = ex.getMessage() == null ? status.getReasonPhrase() : ex.getMessage();
        return ResponseEntity.status(status).body(new ErrorResponse(message, code));
    }
}
