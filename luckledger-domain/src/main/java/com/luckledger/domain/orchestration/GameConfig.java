package com.luckledger.domain.orchestration;

import com.luckledger.domain.generation.MetadataVisibility;
import com.luckledger.domain.generation.NearMissMode;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.pool.PoolContract;
import java.util.Objects;

/**
 * The configuration for setting up (or restocking) a game: the pool to generate, the mechanic to
 * generate it with, the theme to skin it, how to distribute it (book count, dealer count), and
 * whether losing tickets should have near-misses engineered into them.
 *
 * @param poolContract the validated pool to generate; never {@code null}
 * @param mechanicType the mechanic to generate with; never {@code null}
 * @param themeId the id of the theme to skin tickets with; non-blank
 * @param bookCount how many books to partition the batch into; {@code >= 1}
 * @param dealerCount how many NPC dealers to allocate to; {@code >= 1}
 * @param nearMissMode whether to engineer near-misses into losers; never {@code null}. This is
 *     RTP-neutral — it changes only how losing grids are arranged, never the tier counts or payout.
 * @param bookMetadataVisibility how much of a book's depletion state the operator reveals to players;
 *     never {@code null}. Purely a data/UI concern — it does not touch the pool, tier counts, or payout.
 */
public record GameConfig(
        PoolContract poolContract,
        MechanicType mechanicType,
        String themeId,
        int bookCount,
        int dealerCount,
        NearMissMode nearMissMode,
        MetadataVisibility bookMetadataVisibility) {

    public GameConfig {
        Objects.requireNonNull(poolContract, "poolContract must not be null");
        Objects.requireNonNull(mechanicType, "mechanicType must not be null");
        Objects.requireNonNull(themeId, "themeId must not be null");
        Objects.requireNonNull(nearMissMode, "nearMissMode must not be null");
        Objects.requireNonNull(bookMetadataVisibility, "bookMetadataVisibility must not be null");
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

    /**
     * Backwards-compatible constructor defaulting {@code nearMissMode} to {@link NearMissMode#CLEAN}
     * and {@code bookMetadataVisibility} to {@link MetadataVisibility#PARTIAL}.
     *
     * <p>Retained so existing callers (api-side seed configs, the CLI, and their tests) that predate
     * the near-miss feature keep compiling and behaving exactly as before — a CLEAN pool ships no
     * engineered near-misses.
     */
    public GameConfig(
            PoolContract poolContract,
            MechanicType mechanicType,
            String themeId,
            int bookCount,
            int dealerCount) {
        this(poolContract, mechanicType, themeId, bookCount, dealerCount,
                NearMissMode.CLEAN, MetadataVisibility.PARTIAL);
    }

    /**
     * Backwards-compatible constructor defaulting {@code bookMetadataVisibility} to
     * {@link MetadataVisibility#PARTIAL}.
     *
     * <p>Retained so callers that set a {@link NearMissMode} but predate the visibility feature keep
     * compiling unchanged — visibility is a data/UI concern that never affects generation or RTP.
     */
    public GameConfig(
            PoolContract poolContract,
            MechanicType mechanicType,
            String themeId,
            int bookCount,
            int dealerCount,
            NearMissMode nearMissMode) {
        this(poolContract, mechanicType, themeId, bookCount, dealerCount,
                nearMissMode, MetadataVisibility.PARTIAL);
    }
}
