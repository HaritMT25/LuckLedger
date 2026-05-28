package com.luckledger.domain.generation.theme;

import java.util.Objects;

/**
 * The themed presentation of one abstract grid symbol. A mechanic emits abstract symbols (e.g.
 * {@code "SYM_A"}); a theme maps each to a player-facing visual — an emoji, an optional image, and a
 * label. The abstract symbol is what the evaluator scores; the rest is purely cosmetic.
 *
 * @param abstractSymbol the mechanic's internal symbol id; non-blank
 * @param displayEmoji the emoji shown for this symbol; non-blank
 * @param displayImageUrl an optional richer image; {@code null} when none, otherwise non-blank
 * @param displayLabel a human-readable label; non-blank
 */
public record ThemedSymbol(
        String abstractSymbol, String displayEmoji, String displayImageUrl, String displayLabel) {

    public ThemedSymbol {
        Objects.requireNonNull(abstractSymbol, "abstractSymbol must not be null");
        Objects.requireNonNull(displayEmoji, "displayEmoji must not be null");
        Objects.requireNonNull(displayLabel, "displayLabel must not be null");
        if (abstractSymbol.isBlank()) {
            throw new IllegalArgumentException("abstractSymbol must not be blank");
        }
        if (displayEmoji.isBlank()) {
            throw new IllegalArgumentException("displayEmoji must not be blank");
        }
        if (displayLabel.isBlank()) {
            throw new IllegalArgumentException("displayLabel must not be blank");
        }
        if (displayImageUrl != null && displayImageUrl.isBlank()) {
            throw new IllegalArgumentException("displayImageUrl must be null or non-blank, was blank");
        }
    }
}
