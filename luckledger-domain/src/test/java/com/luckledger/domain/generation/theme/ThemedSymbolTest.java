package com.luckledger.domain.generation.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ThemedSymbolTest {

    @Test
    void holdsItsFields() {
        ThemedSymbol symbol = new ThemedSymbol("SYM_A", "🤠", "/assets/cowboy.png", "Cowboy Hat");

        assertThat(symbol.abstractSymbol()).isEqualTo("SYM_A");
        assertThat(symbol.displayEmoji()).isEqualTo("🤠");
        assertThat(symbol.displayImageUrl()).isEqualTo("/assets/cowboy.png");
        assertThat(symbol.displayLabel()).isEqualTo("Cowboy Hat");
    }

    @Test
    void nullImageUrlIsAllowed() {
        ThemedSymbol symbol = new ThemedSymbol("SYM_A", "🤠", null, "Cowboy Hat");

        assertThat(symbol.displayImageUrl()).isNull();
    }

    @Test
    void blankImageUrlIsRejected() {
        assertThatThrownBy(() -> new ThemedSymbol("SYM_A", "x", "  ", "label"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullRequiredFieldIsRejected() {
        assertThatThrownBy(() -> new ThemedSymbol(null, "x", null, "l"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ThemedSymbol("s", null, null, "l"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ThemedSymbol("s", "x", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankRequiredFieldIsRejected() {
        assertThatThrownBy(() -> new ThemedSymbol(" ", "x", null, "l"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThemedSymbol("s", " ", null, "l"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThemedSymbol("s", "x", null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
