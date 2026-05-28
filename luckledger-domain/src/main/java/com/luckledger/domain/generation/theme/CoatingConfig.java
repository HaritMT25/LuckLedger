package com.luckledger.domain.generation.theme;

import java.util.List;
import java.util.Objects;

/**
 * Parameters for the metallic coating the player scratches away. The coating is rendered
 * procedurally at canvas-init time (not a baked asset): a base colour, a gradient ramp, a noise
 * intensity, and the hatch pattern's angle and spacing.
 *
 * @param baseColor the coating's base colour string; non-blank
 * @param gradientStops ordered gradient colour stops; non-empty, no blank entries, held as an
 *     unmodifiable copy
 * @param noiseIntensity grain applied over the coating, in {@code [0.0, 1.0]}
 * @param hatchAngle hatch line angle in degrees, in {@code [0, 360]}
 * @param hatchSpacing hatch line spacing in pixels; {@code > 0}
 */
public record CoatingConfig(
        String baseColor,
        List<String> gradientStops,
        double noiseIntensity,
        int hatchAngle,
        int hatchSpacing) {

    public CoatingConfig {
        Objects.requireNonNull(baseColor, "baseColor must not be null");
        Objects.requireNonNull(gradientStops, "gradientStops must not be null");
        if (baseColor.isBlank()) {
            throw new IllegalArgumentException("baseColor must not be blank");
        }
        if (gradientStops.isEmpty()) {
            throw new IllegalArgumentException("gradientStops must not be empty");
        }
        for (String stop : gradientStops) {
            if (stop == null || stop.isBlank()) {
                throw new IllegalArgumentException("gradientStops must not contain null or blank entries");
            }
        }
        if (noiseIntensity < 0.0 || noiseIntensity > 1.0) {
            throw new IllegalArgumentException("noiseIntensity must be in [0.0, 1.0], was " + noiseIntensity);
        }
        if (hatchAngle < 0 || hatchAngle > 360) {
            throw new IllegalArgumentException("hatchAngle must be in [0, 360], was " + hatchAngle);
        }
        if (hatchSpacing <= 0) {
            throw new IllegalArgumentException("hatchSpacing must be > 0, was " + hatchSpacing);
        }
        gradientStops = List.copyOf(gradientStops);
    }
}
