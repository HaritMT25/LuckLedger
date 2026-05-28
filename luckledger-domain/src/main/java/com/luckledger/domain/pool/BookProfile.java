package com.luckledger.domain.pool;

/**
 * Risk profile of a ticket book that shapes the prize distribution across books.
 *
 * <p>All profiles maintain the same payout ratio (RTP); they differ only in how the
 * prize budget is spread across tiers. The expected return per coin is identical for
 * every profile — the difference is purely in win frequency versus max prize size,
 * which is the core educational trap LuckLedger illustrates.
 */
public enum BookProfile {

    /** Many small prizes, high win frequency, low maximum prize. */
    CONSERVATIVE,

    /** Moderate spread between win frequency and prize size. */
    BALANCED,

    /** One huge prize with mostly losers; low win frequency, high maximum prize. */
    JACKPOT
}
