package com.luckledger.api;

import java.util.UUID;

/**
 * Thrown when a reveal is attempted by someone other than the ticket's buyer. Purchase is anonymous —
 * whoever holds the player id IS the buyer — so buying is not gated; but reveal is where a prize is
 * credited to an account, so crediting a <em>foreign</em> account must be refused. Mapped to
 * {@code 403 NOT_TICKET_OWNER}.
 */
public class TicketOwnershipException extends RuntimeException {

    public TicketOwnershipException(UUID ticketId, UUID owner, UUID claimant) {
        super("player " + claimant + " may not reveal ticket " + ticketId + " owned by " + owner);
    }
}
