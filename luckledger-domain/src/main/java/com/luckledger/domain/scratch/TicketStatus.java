package com.luckledger.domain.scratch;

/**
 * Lifecycle of a single ticket in the scratch flow: {@link #AVAILABLE} in a book, {@link #SOLD} once
 * a player buys it (purchase), {@link #REVEALED} once they scratch it (reveal). Purchase and reveal
 * are separate operations, so SOLD and REVEALED are distinct states.
 */
public enum TicketStatus {
    AVAILABLE,
    SOLD,
    REVEALED
}
