package com.luckledger.api;

import java.util.UUID;

/**
 * Thrown when a reveal is attempted by someone other than the ticket's buyer. Purchase is anonymous —
 * whoever holds the player id IS the buyer — so buying is not gated; but reveal is where a prize is
 * credited to an account, so crediting a <em>foreign</em> account must be refused. Mapped to
 * {@code 403 NOT_TICKET_OWNER}.
 *
 * <p>The detailed message names the owner's player id, which is that anonymous player's sole bearer
 * credential — so it must be logged server-side only and NEVER echoed to the non-owning caller. The
 * ids are retained as fields so {@code GlobalExceptionHandler} can log the detail while returning a
 * generic client body.
 */
public class TicketOwnershipException extends RuntimeException {

    private final UUID ticketId;
    private final UUID owner;
    private final UUID claimant;

    public TicketOwnershipException(UUID ticketId, UUID owner, UUID claimant) {
        super("player " + claimant + " may not reveal ticket " + ticketId + " owned by " + owner);
        this.ticketId = ticketId;
        this.owner = owner;
        this.claimant = claimant;
    }

    public UUID ticketId() {
        return ticketId;
    }

    public UUID owner() {
        return owner;
    }

    public UUID claimant() {
        return claimant;
    }
}
