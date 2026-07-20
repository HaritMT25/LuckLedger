package com.luckledger.domain.orchestration;

/**
 * The lifecycle state of a generated game (campaign).
 *
 * <p><strong>Why this exists.</strong> A campaign's economics are fixed at generation time — the
 * return-to-player is derived from its tier structure and can never be retuned in place (retuning is,
 * by construction, a new campaign). What the operator <em>can</em> do is stop selling an existing
 * campaign. Retiring flips this status only: it never touches already-sold tickets or the append-only
 * ledger, so a player who already bought a ticket can still reveal it and be paid.
 */
public enum GameStatus {

    /** The campaign is live: its books are stocked and its tickets can be purchased. */
    ACTIVE,

    /**
     * The campaign is withdrawn from sale: no new purchases are accepted and it cannot be restocked.
     * Reveals of tickets already sold keep working — the ledger and outstanding tickets are untouched.
     */
    RETIRED
}
