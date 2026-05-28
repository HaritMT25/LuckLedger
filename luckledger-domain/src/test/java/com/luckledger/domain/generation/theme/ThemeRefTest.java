package com.luckledger.domain.generation.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThemeRefTest {

    private static final ThemedSymbol SYMBOL =
            new ThemedSymbol("SYM_A", "🤠", null, "Cowboy Hat");
    private static final ColorPalette PALETTE =
            new ColorPalette("#1", "#2", "#3", "#4", "#5");
    private static final AssetRef BG = new AssetRef("/bg.png");
    private static final CoatingConfig COATING =
            new CoatingConfig("#C4A535", java.util.List.of("#1", "#2"), 0.6, 45, 5);

    private static ThemeRef themeWith(Map<String, ThemedSymbol> symbols, AssetRef sparkle) {
        return new ThemeRef("texas", "Texas Hold'em", symbols, PALETTE, BG, COATING, sparkle);
    }

    @Test
    void holdsAllComponents() {
        ThemeRef theme = themeWith(Map.of("SYM_A", SYMBOL), new AssetRef("/sparkle.gif"));

        assertThat(theme.themeId()).isEqualTo("texas");
        assertThat(theme.name()).isEqualTo("Texas Hold'em");
        assertThat(theme.symbolMap()).containsEntry("SYM_A", SYMBOL);
        assertThat(theme.palette()).isEqualTo(PALETTE);
        assertThat(theme.backgroundArt()).isEqualTo(BG);
        assertThat(theme.coatingConfig()).isEqualTo(COATING);
        assertThat(theme.sparkleGif()).isEqualTo(new AssetRef("/sparkle.gif"));
    }

    @Test
    void sparkleGifIsOptional() {
        ThemeRef theme = themeWith(Map.of("SYM_A", SYMBOL), null);

        assertThat(theme.sparkleGif()).isNull();
    }

    @Test
    void symbolMapIsAnUnmodifiableCopy() {
        Map<String, ThemedSymbol> source = new HashMap<>();
        source.put("SYM_A", SYMBOL);
        ThemeRef theme = themeWith(source, null);

        source.put("SYM_B", SYMBOL);
        assertThat(theme.symbolMap()).doesNotContainKey("SYM_B");
        assertThatThrownBy(() -> theme.symbolMap().put("SYM_C", SYMBOL))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void blankIdentifiersAreRejected() {
        assertThatThrownBy(() -> new ThemeRef(" ", "n", Map.of(), PALETTE, BG, COATING, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThemeRef("id", " ", Map.of(), PALETTE, BG, COATING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullRequiredComponentIsRejected() {
        assertThatThrownBy(() -> new ThemeRef("id", "n", null, PALETTE, BG, COATING, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ThemeRef("id", "n", Map.of(), null, BG, COATING, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ThemeRef("id", "n", Map.of(), PALETTE, null, COATING, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ThemeRef("id", "n", Map.of(), PALETTE, BG, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
