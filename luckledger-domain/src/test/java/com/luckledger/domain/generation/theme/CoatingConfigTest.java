package com.luckledger.domain.generation.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoatingConfigTest {

    private static CoatingConfig valid(List<String> stops) {
        return new CoatingConfig("#C4A535", stops, 0.6, 45, 5);
    }

    @Test
    void holdsItsFields() {
        CoatingConfig config = valid(List.of("#8B6914", "#C4A535", "#DAB94A"));

        assertThat(config.baseColor()).isEqualTo("#C4A535");
        assertThat(config.gradientStops()).containsExactly("#8B6914", "#C4A535", "#DAB94A");
        assertThat(config.noiseIntensity()).isEqualTo(0.6);
        assertThat(config.hatchAngle()).isEqualTo(45);
        assertThat(config.hatchSpacing()).isEqualTo(5);
    }

    @Test
    void gradientStopsAreAnUnmodifiableCopy() {
        List<String> source = new ArrayList<>(List.of("#1", "#2"));
        CoatingConfig config = valid(source);

        source.add("#3");
        assertThat(config.gradientStops()).hasSize(2);
        assertThatThrownBy(() -> config.gradientStops().add("#4"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void blankBaseColorIsRejected() {
        assertThatThrownBy(() -> new CoatingConfig(" ", List.of("#1"), 0.5, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyGradientStopsIsRejected() {
        assertThatThrownBy(() -> valid(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankGradientStopIsRejected() {
        assertThatThrownBy(() -> valid(List.of("#1", " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noiseIntensityOutsideUnitIntervalIsRejected() {
        assertThatThrownBy(() -> new CoatingConfig("#1", List.of("#1"), 1.1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoatingConfig("#1", List.of("#1"), -0.1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hatchAngleOutsideRangeIsRejected() {
        assertThatThrownBy(() -> new CoatingConfig("#1", List.of("#1"), 0.5, 361, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoatingConfig("#1", List.of("#1"), 0.5, -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveHatchSpacingIsRejected() {
        assertThatThrownBy(() -> new CoatingConfig("#1", List.of("#1"), 0.5, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
