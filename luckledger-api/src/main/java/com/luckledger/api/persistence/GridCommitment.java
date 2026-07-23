package com.luckledger.api.persistence;

import com.luckledger.api.persistence.GridCodec.CellDto;
import com.luckledger.api.persistence.GridCodec.GridDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * The cryptographic commit-reveal proof that a ticket's outcome existed before it was scratched.
 *
 * <p>At generation time every ticket is stamped with a random {@code salt} and a
 * {@code commitment} = SHA-256 over a canonical encoding of its mechanic grid. The commitment is
 * public from purchase; the salt is revealed only when the player scratches. Because the salt is
 * withheld, a player who knows only the commitment cannot brute-force the small grid space to learn
 * the outcome early — yet after reveal anyone can re-hash the grid with the salt and confirm it
 * equals the commitment that was fixed when the pool was printed.
 *
 * <p><strong>CANONICAL ENCODING (the single source of truth — mirrored byte-for-byte in
 * {@code app.js}'s {@code commitmentCanonical}):</strong>
 * <pre>{@code
 *   salt + "|" + rows + "x" + cols + "|" + symbols.join(",")
 * }</pre>
 * where {@code rows}/{@code cols} are the grid dimensions (a square, so both equal
 * {@link GridDto#dimension()}), and {@code symbols} is every cell's {@link CellDto#symbol() symbol}
 * string in row-major order (sorted by row, then column). A cell also carries a {@code prizeValue};
 * it is <em>deliberately excluded</em> — only the symbol is canonicalized, so the encoding depends on
 * exactly the visible grid layout the player scratches. The string is encoded as UTF-8 bytes, hashed
 * with SHA-256, and rendered as lowercase hex (64 chars). The salt is 16 random bytes from
 * {@link SecureRandom}, rendered as lowercase hex (32 chars).
 *
 * <p><strong>Isolation:</strong> the salt is persistence-side randomness only. It never touches
 * domain generation or any RNG used to build grids, so it cannot perturb outcomes or RTP.
 *
 * @param salt the 32-char lowercase-hex salt; secret until reveal
 * @param commitment the 64-char lowercase-hex SHA-256 of the canonical encoding; public from purchase
 */
record GridCommitment(String salt, String commitment) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;

    /** Stamps a grid with a freshly generated salt and its resulting commitment. */
    static GridCommitment forGrid(GridDto grid) {
        return forGrid(grid, newSalt());
    }

    /** Computes the commitment for a grid under a specific salt (the deterministic core; testable). */
    static GridCommitment forGrid(GridDto grid, String salt) {
        return new GridCommitment(salt, hash(canonicalEncoding(grid, salt)));
    }

    /**
     * Builds the canonical encoding string for a grid and salt. See the class javadoc for the exact,
     * mirrored format.
     */
    static String canonicalEncoding(GridDto grid, String salt) {
        int rows = grid.dimension();
        int cols = grid.dimension();
        String symbols = grid.cells().stream()
                .sorted(Comparator.comparingInt(CellDto::row).thenComparingInt(CellDto::col))
                .map(CellDto::symbol)
                .collect(Collectors.joining(","));
        return salt + "|" + rows + "x" + cols + "|" + symbols;
    }

    /** A fresh 32-char lowercase-hex salt from {@link SecureRandom} (16 random bytes). */
    static String newSalt() {
        byte[] bytes = new byte[SALT_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Lowercase-hex SHA-256 of the canonical encoding's UTF-8 bytes. */
    private static String hash(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandated algorithm on every JVM; its absence is unrecoverable.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
