package com.luckledger.domain.generation.theme;

import java.util.Objects;

/**
 * The five-colour scheme a theme applies when skinning a ticket — primary, secondary, accent,
 * background, and text. Values are renderer-facing colour strings (typically hex such as
 * {@code "#8B6914"}); each must be present and non-blank.
 */
public record ColorPalette(
        String primary, String secondary, String accent, String background, String text) {

    public ColorPalette {
        requireNonBlank(primary, "primary");
        requireNonBlank(secondary, "secondary");
        requireNonBlank(accent, "accent");
        requireNonBlank(background, "background");
        requireNonBlank(text, "text");
    }

    private static void requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
