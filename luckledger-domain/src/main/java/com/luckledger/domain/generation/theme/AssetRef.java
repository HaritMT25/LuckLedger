package com.luckledger.domain.generation.theme;

import java.util.Objects;

/**
 * A reference to a static theme asset — background art, a sparkle GIF, an icon — by its path or URL.
 * The asset itself lives outside the domain; this is only the pointer the renderer resolves.
 *
 * @param path the asset path or URL; non-blank
 */
public record AssetRef(String path) {

    public AssetRef {
        Objects.requireNonNull(path, "path must not be null");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
    }
}
