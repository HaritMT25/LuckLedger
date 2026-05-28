package com.luckledger.domain.orchestration;

import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.pool.PoolContract;
import java.util.Objects;

/**
 * The configuration for setting up (or restocking) a game: the pool to generate, the mechanic to
 * generate it with, the theme to skin it, and how to distribute it (book count, dealer count).
 *
 * @param poolContract the validated pool to generate; never {@code null}
 * @param mechanicType the mechanic to generate with; never {@code null}
 * @param themeId the id of the theme to skin tickets with; non-blank
 * @param bookCount how many books to partition the batch into; {@code >= 1}
 * @param dealerCount how many NPC dealers to allocate to; {@code >= 1}
 */
public record GameConfig(
        PoolContract poolContract, MechanicType mechanicType, String themeId, int bookCount, int dealerCount) {

    public GameConfig {
        Objects.requireNonNull(poolContract, "poolContract must not be null");
        Objects.requireNonNull(mechanicType, "mechanicType must not be null");
        Objects.requireNonNull(themeId, "themeId must not be null");
        if (themeId.isBlank()) {
            throw new IllegalArgumentException("themeId must not be blank");
        }
        if (bookCount < 1) {
            throw new IllegalArgumentException("bookCount must be >= 1, was " + bookCount);
        }
        if (dealerCount < 1) {
            throw new IllegalArgumentException("dealerCount must be >= 1, was " + dealerCount);
        }
    }
}
