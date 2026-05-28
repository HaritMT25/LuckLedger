package com.luckledger.domain.generation.theme;

import java.util.Map;
import java.util.Objects;

/**
 * A complete theme — the data (never a class hierarchy) that skins an abstract ticket into a
 * renderable one. It maps each abstract grid symbol to its themed visual and bundles the palette,
 * scratch-coating parameters, and background/sparkle art. Loaded from storage as a value object.
 *
 * @param themeId stable theme identifier; non-blank
 * @param name human-readable theme name; non-blank
 * @param symbolMap abstract symbol → themed visual; non-null, held as an unmodifiable copy, no null
 *     keys or values
 * @param palette the colour scheme; non-null
 * @param backgroundArt the background asset; non-null
 * @param coatingConfig the scratch-coating parameters; non-null
 * @param sparkleGif an optional win-celebration overlay; {@code null} when none
 */
public record ThemeRef(
        String themeId,
        String name,
        Map<String, ThemedSymbol> symbolMap,
        ColorPalette palette,
        AssetRef backgroundArt,
        CoatingConfig coatingConfig,
        AssetRef sparkleGif) {

    public ThemeRef {
        Objects.requireNonNull(themeId, "themeId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(symbolMap, "symbolMap must not be null");
        Objects.requireNonNull(palette, "palette must not be null");
        Objects.requireNonNull(backgroundArt, "backgroundArt must not be null");
        Objects.requireNonNull(coatingConfig, "coatingConfig must not be null");
        if (themeId.isBlank()) {
            throw new IllegalArgumentException("themeId must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        symbolMap = Map.copyOf(symbolMap); // rejects null keys/values, makes an unmodifiable copy
    }
}
