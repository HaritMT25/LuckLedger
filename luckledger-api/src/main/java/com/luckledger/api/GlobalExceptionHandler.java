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
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates domain exceptions into the uniform {@link ErrorResponse} envelope with the appropriate
 * HTTP status, so controllers never have to build error responses themselves.
 *
 * <ul>
 *   <li>{@code 400} — a malformed request body/param, or a bean-validation failure on a {@code @Valid} body
 *   <li>{@code 402} — insufficient balance to buy a ticket
 *   <li>{@code 403} — a player tried to reveal a ticket owned by someone else
 *   <li>{@code 404} — unknown id (player, game, ticket, ...) or no such route
 *   <li>{@code 405} — HTTP method not allowed for the route
 *   <li>{@code 409} — illegal state (e.g. an unsold ticket, or a dealer at capacity)
 *   <li>{@code 410} — the book is depleted
 *   <li>{@code 415} — unsupported request media type
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

    /**
     * A player tried to reveal a ticket they do not own: 403. The client body is deliberately generic —
     * echoing {@code ex.getMessage()} would leak the owner's player id (an anonymous player's sole
     * bearer credential) to a non-owner. The full detail is logged server-side at WARN instead.
     */
    @ExceptionHandler(TicketOwnershipException.class)
    public ResponseEntity<ErrorResponse> handleNotTicketOwner(TicketOwnershipException ex) {
        log.warn("Ticket ownership refused: claimant {} may not reveal ticket {} owned by {}",
                ex.claimant(), ex.ticketId(), ex.owner());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("You do not own this ticket.", "NOT_TICKET_OWNER"));
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

    /** A path variable or query param could not be bound to its target type (e.g. a malformed UUID): 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Malformed request parameter.", "MALFORMED_REQUEST"));
    }

    /** A required query parameter was absent: 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Missing required request parameter.", "MALFORMED_REQUEST"));
    }

    /** The HTTP method is not supported by the matched route: 405. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("HTTP method not allowed for this endpoint.", "METHOD_NOT_ALLOWED"));
    }

    /** The request's {@code Content-Type} is not one the endpoint accepts: 415. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse("Unsupported media type.", "UNSUPPORTED_MEDIA_TYPE"));
    }

    /** No route matched the request path (Boot 3.2+ raises this instead of returning a bare 404): 404. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No such resource.", "NOT_FOUND"));
    }

    /**
     * Last-resort handler for anything unmapped. Security exceptions are rethrown so Spring Security's
     * own entry point / access-denied handler answers them (401/403), and any remaining Spring
     * {@link org.springframework.web.ErrorResponse} (a framework 4xx we did not map explicitly) is
     * rethrown so it keeps its own default status rather than being flattened to 500. Everything else
     * becomes an opaque 500 that never leaks the underlying message, stack trace, or type to the caller.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) throws Exception {
        if (ex instanceof AccessDeniedException
                || ex instanceof AuthenticationException
                || ex instanceof org.springframework.web.ErrorResponse) {
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
