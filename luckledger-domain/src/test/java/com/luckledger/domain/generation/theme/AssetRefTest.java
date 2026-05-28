package com.luckledger.domain.generation.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AssetRefTest {

    @Test
    void holdsItsPath() {
        AssetRef ref = new AssetRef("/assets/themes/texas/background.png");

        assertThat(ref.path()).isEqualTo("/assets/themes/texas/background.png");
    }

    @Test
    void nullPathIsRejected() {
        assertThatThrownBy(() -> new AssetRef(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankPathIsRejected() {
        assertThatThrownBy(() -> new AssetRef("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
