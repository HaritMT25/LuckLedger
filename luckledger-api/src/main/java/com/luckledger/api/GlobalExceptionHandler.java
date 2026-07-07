package com.luckledger.api;

import com.luckledger.distribution.BookDepletedException;
import com.luckledger.domain.generation.GenerationIntegrityException;
import com.luckledger.domain.player.InsufficientBalanceException;
import com.luckledger.domain.pool.InvalidPoolException;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into the uniform {@link ErrorResponse} envelope with the appropriate
 * HTTP status, so controllers never have to build error responses themselves.
 *
 * <ul>
 *   <li>{@code 400} — a malformed request body, or a bean-validation failure on a {@code @Valid} body
 *   <li>{@code 402} — insufficient balance to buy a ticket
 *   <li>{@code 403} — a player tried to reveal a ticket owned by someone else
 *   <li>{@code 404} — unknown id (player, game, ticket, ...)
 *   <li>{@code 409} — illegal state (e.g. an unsold ticket, or a dealer at capacity)
 *   <li>{@code 410} — the book is depleted
 *   <li>{@code 422} — invalid input that reaches the domain (invalid pool / illegal argument)
 *   <li>{@code 500} — generation integrity failure, or an unexpected error (never leaks internals)
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        return build(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_BALANCE", ex);
    }

    @ExceptionHandler(TicketOwnershipException.class)
    public ResponseEntity<ErrorResponse> handleNotTicketOwner(TicketOwnershipException ex) {
        return build(HttpStatus.FORBIDDEN, "NOT_TICKET_OWNER", ex);
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
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", ex);
    }

    /** A {@code @Valid} request body failed bean validation before the controller ran: 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Request validation failed.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message, "VALIDATION_ERROR"));
    }

    /** The request body could not be parsed (missing/garbled JSON, wrong type): 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Malformed or missing request body.", "MALFORMED_REQUEST"));
    }

    @ExceptionHandler(GenerationIntegrityException.class)
    public ResponseEntity<ErrorResponse> handleGenerationIntegrity(GenerationIntegrityException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "GENERATION_FAILED", ex);
    }

    /**
     * Last-resort handler for anything unmapped. Security exceptions are rethrown so Spring Security's
     * own entry point / access-denied handler answers them (401/403); everything else becomes an opaque
     * 500 that never leaks the underlying message, stack trace, or type to the caller.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unhandled exception serving a request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected error occurred.", "INTERNAL_ERROR"));
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, Exception ex) {
        String message = ex.getMessage() == null ? status.getReasonPhrase() : ex.getMessage();
        return ResponseEntity.status(status).body(new ErrorResponse(message, code));
    }
}
