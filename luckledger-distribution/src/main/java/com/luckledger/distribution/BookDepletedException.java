package com.luckledger.distribution;

/**
 * Unchecked exception thrown when a caller asks a fully-sold {@code TicketBook} for another ticket.
 * Books are sold strictly sequentially; once depleted there is no next ticket to hand out.
 */
public class BookDepletedException extends RuntimeException {

    public BookDepletedException(String message) {
        super(message);
    }

    public BookDepletedException(String message, Throwable cause) {
        super(message, cause);
    }
}
