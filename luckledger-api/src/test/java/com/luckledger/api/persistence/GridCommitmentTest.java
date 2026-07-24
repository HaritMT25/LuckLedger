package com.luckledger.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.api.persistence.GridCodec.CellDto;
import com.luckledger.api.persistence.GridCodec.GridDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Locks the commit-reveal canonical encoding and hash byte-for-byte. The JS mirror in {@code js/views/play.js}
 * must reproduce exactly the same strings and digest.
 */
class GridCommitmentTest {

    // A hand-built 2x2 mechanic grid, cells intentionally out of row-major order to prove the encoder
    // sorts them. Only the symbol is canonicalized; prizeValue must NOT enter the encoding.
    private static final GridDto GRID = new GridDto(
            "SIZE_2X2",
            2,
            List.of(
                    new CellDto(1, 1, "SYM_D", 0.0),
                    new CellDto(0, 0, "SYM_A", 300.0),
                    new CellDto(0, 1, "SYM_B", 0.0),
                    new CellDto(1, 0, "SYM_C", 0.0)));

    private static final String SALT = "0123456789abcdef0123456789abcdef";

    @Test
    void canonicalEncodingIsSaltPipeDimsPipeRowMajorSymbols() {
        String encoding = GridCommitment.canonicalEncoding(GRID, SALT);
        assertThat(encoding).isEqualTo(SALT + "|2x2|SYM_A,SYM_B,SYM_C,SYM_D");
    }

    @Test
    void commitmentIsLowercaseHexSha256OfTheCanonicalEncoding() throws NoSuchAlgorithmException {
        String canonical = SALT + "|2x2|SYM_A,SYM_B,SYM_C,SYM_D";
        byte[] expectedDigest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
        String expectedHex = HexFormat.of().formatHex(expectedDigest);

        GridCommitment commitment = GridCommitment.forGrid(GRID, SALT);

        assertThat(commitment.commitment()).isEqualTo(expectedHex);
        assertThat(commitment.commitment()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(commitment.salt()).isEqualTo(SALT);
    }

    @Test
    void newSaltIs32LowercaseHexCharsAndRandom() {
        String first = GridCommitment.newSalt();
        String second = GridCommitment.newSalt();

        assertThat(first).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(second).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void forGridGeneratesAFreshSaltWhenNoneSupplied() {
        GridCommitment a = GridCommitment.forGrid(GRID);
        GridCommitment b = GridCommitment.forGrid(GRID);

        assertThat(a.salt()).hasSize(32).matches("[0-9a-f]{32}");
        // Different salts -> different commitments for the same grid (the whole point of the salt).
        assertThat(a.salt()).isNotEqualTo(b.salt());
        assertThat(a.commitment()).isNotEqualTo(b.commitment());
    }
}
