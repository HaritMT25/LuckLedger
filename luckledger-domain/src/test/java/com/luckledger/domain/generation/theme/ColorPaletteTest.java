package com.luckledger.domain.generation.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ColorPaletteTest {

    @Test
    void holdsAllFiveColors() {
        ColorPalette palette =
                new ColorPalette("#8B6914", "#C4A535", "#FFD700", "#1A0F00", "#E8D5B0");

        assertThat(palette.primary()).isEqualTo("#8B6914");
        assertThat(palette.secondary()).isEqualTo("#C4A535");
        assertThat(palette.accent()).isEqualTo("#FFD700");
        assertThat(palette.background()).isEqualTo("#1A0F00");
        assertThat(palette.text()).isEqualTo("#E8D5B0");
    }

    @Test
    void nullColorIsRejected() {
        assertThatThrownBy(() -> new ColorPalette(null, "#1", "#2", "#3", "#4"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankColorIsRejected() {
        assertThatThrownBy(() -> new ColorPalette("#1", "  ", "#2", "#3", "#4"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ColorPalette("#1", "#2", "#3", "#4", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
