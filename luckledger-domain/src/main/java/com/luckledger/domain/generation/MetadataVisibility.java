package com.luckledger.domain.generation;

/**
 * How much of a book's live depletion state the operator chooses to reveal to a player.
 *
 * <p><strong>Why this exists (education-first).</strong> A real scratch-card roll is opaque: the
 * player cannot see how many prizes are left, only that tickets are being sold. Some players still
 * try to "read the roll" — asking a clerk for a fresh book, or tracking how many big winners a game
 * has paid out — believing a book that has sold many losers is now "due". LuckLedger makes that
 * hidden state a dial the operator can turn, at three transparency tiers, so the simulator can show
 * exactly what such information does (and does not) buy the player.
 *
 * <p><strong>Crucially, none of these tiers changes a single per-ticket probability.</strong> The
 * pool was fixed at print time: every ticket's outcome was decided before the first sale. Seeing that
 * 60% of a book has been dispensed tells you about the <em>past</em>, not about the still-sealed
 * ticket in your hand — that ticket's odds are whatever the pool made them, regardless of how much of
 * the roll is visible. The "payout ratio is sacred" invariant is untouched; visibility is a UI/data
 * concern only.
 *
 * @see com.luckledger.domain.orchestration.GameConfig
 */
public enum MetadataVisibility {

    /**
     * The opaque roll. The player sees only ticket counts (total and remaining) — nothing about
     * prizes dispensed or value left. This is the honest default for a physical scratch card: you
     * cannot read the roll at all.
     */
    NONE,

    /**
     * A partial peek: the player additionally sees the percentage of the book already dispensed. It
     * looks like actionable "the book is running down" information, but it still reveals nothing about
     * whether the remaining tickets are winners — a deliberate teaching contrast with {@link #NONE}.
     */
    PARTIAL,

    /**
     * The open book: on top of {@link #PARTIAL}'s percentage, the player sees the prize value
     * dispensed so far, the estimated value still in the book, and how frequently the revealed tickets
     * have won. Even here, per-ticket odds at purchase are unchanged — the point is to prove that full
     * transparency about the roll's history does not make the next sealed ticket any more likely to win.
     */
    FULL
}
