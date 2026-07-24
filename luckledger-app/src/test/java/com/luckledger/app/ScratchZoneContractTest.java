package com.luckledger.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luckledger.mechanic.DemonSealEvaluator;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins the frontend scratch-zone config (static/config/scratch-zones.json) to the mechanic
 * geometry it must render. This is a plain contract test (no Spring context): if the JSON drifts
 * from what the Celestial Fortune / Demon Seal mechanics and the frontend's zone-order constant
 * expect, the build breaks here instead of silently mis-rendering scratch tickets.
 */
class ScratchZoneContractTest {

    /** Mirrors SEAL_ZONE_ORDER in static/js/core.js — update both together. */
    private static final List<String> SEAL_ZONE_ORDER = List.of(
            "seal-top", "seal-upper-right", "seal-lower-right", "seal-bottom", "seal-lower-left", "seal-upper-left");

    private static JsonNode root;

    @BeforeAll
    static void loadConfig() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = ScratchZoneContractTest.class
                .getResourceAsStream("/static/config/scratch-zones.json")) {
            assertThat(in).as("scratch-zones.json must be on the classpath").isNotNull();
            root = mapper.readTree(in);
        }
    }

    @Test
    void shouldExposeExactly16CelestialScratchZones_withWinRowAboveNumRow() {
        List<JsonNode> zones = zonesFor("CELESTIAL_FORTUNE");
        List<JsonNode> scratchZones = scratchOnly(zones);

        assertThat(scratchZones).hasSize(16);

        List<String> scratchIds = ids(scratchZones);
        List<String> expectedWinIds = IntStream.rangeClosed(1, 4).mapToObj(i -> "win-" + i).toList();
        List<String> expectedNumIds = IntStream.rangeClosed(1, 12).mapToObj(i -> "num-" + i).toList();
        List<String> expectedIds = new ArrayList<>(expectedWinIds);
        expectedIds.addAll(expectedNumIds);

        assertThat(scratchIds).containsExactlyInAnyOrderElementsOf(expectedIds);

        List<JsonNode> winZones = scratchZones.stream()
                .filter(z -> z.get("id").asText().startsWith("win-"))
                .toList();
        List<JsonNode> numZones = scratchZones.stream()
                .filter(z -> z.get("id").asText().startsWith("num-"))
                .toList();

        double maxWinY = winZones.stream().mapToDouble(this::yOf).max().orElseThrow();
        double minNumY = numZones.stream().mapToDouble(this::yOf).min().orElseThrow();
        assertThat(maxWinY).as("every win zone's y must be strictly above every num zone's y")
                .isLessThan(minNumY);
    }

    @Test
    void shouldExposeExactlySealCountDemonScratchZones_inSealZoneOrder() {
        List<JsonNode> zones = zonesFor("DEMON_SEAL");
        List<JsonNode> scratchZones = scratchOnly(zones);

        assertThat(scratchZones).hasSize(DemonSealEvaluator.SEAL_COUNT);
        assertThat(SEAL_ZONE_ORDER).hasSize(DemonSealEvaluator.SEAL_COUNT);
        assertThat(ids(scratchZones)).containsExactlyElementsOf(SEAL_ZONE_ORDER);
    }

    @Test
    void shouldKeepEveryZoneWithinTheUnitCanvas_forBothMechanics() {
        List<JsonNode> allZones = new ArrayList<>();
        allZones.addAll(zonesFor("CELESTIAL_FORTUNE"));
        allZones.addAll(zonesFor("DEMON_SEAL"));

        assertThat(allZones).isNotEmpty();
        assertThat(allZones).allSatisfy(zone -> {
            double x = boundsXOf(zone);
            double y = boundsYOf(zone);
            double width = widthOf(zone);
            double height = heightOf(zone);

            assertThat(x).as("x of zone %s", zone.get("id").asText()).isGreaterThanOrEqualTo(0.0);
            assertThat(y).as("y of zone %s", zone.get("id").asText()).isGreaterThanOrEqualTo(0.0);
            assertThat(width).as("width of zone %s", zone.get("id").asText()).isGreaterThan(0.0);
            assertThat(height).as("height of zone %s", zone.get("id").asText()).isGreaterThan(0.0);
            assertThat(x + width).as("x+width of zone %s", zone.get("id").asText()).isLessThanOrEqualTo(1.0);
            assertThat(y + height).as("y+height of zone %s", zone.get("id").asText()).isLessThanOrEqualTo(1.0);
        });
    }

    private List<JsonNode> zonesFor(String ticketKey) {
        JsonNode ticket = root.path("tickets").path(ticketKey);
        assertThat(ticket.isMissingNode()).as("ticket %s must exist in scratch-zones.json", ticketKey).isFalse();

        return StreamSupport.stream(ticket.path("zones").spliterator(), false).toList();
    }

    private List<JsonNode> scratchOnly(List<JsonNode> zones) {
        return zones.stream().filter(z -> z.path("scratch").asBoolean(false)).toList();
    }

    private List<String> ids(List<JsonNode> zones) {
        return zones.stream().map(z -> z.get("id").asText()).collect(Collectors.toList());
    }

    /** A zone's y-coordinate, whichever shape it is: rects use {@code y}, circles use {@code cy - r}. */
    private double yOf(JsonNode zone) {
        return "circle".equals(zone.path("shape").asText()) ? boundsYOf(zone) : zone.get("y").asDouble();
    }

    /** Bounding-box x for any shape ({@code x} for rects, {@code cx - r} for circles, min point x for paths). */
    private double boundsXOf(JsonNode zone) {
        return switch (zone.path("shape").asText()) {
            case "circle" -> zone.get("cx").asDouble() - zone.get("r").asDouble();
            case "path" -> pointCoords(zone, "x").min().orElseThrow();
            default -> zone.get("x").asDouble();
        };
    }

    /** Bounding-box y for any shape ({@code y} for rects, {@code cy - r} for circles, min point y for paths). */
    private double boundsYOf(JsonNode zone) {
        return switch (zone.path("shape").asText()) {
            case "circle" -> zone.get("cy").asDouble() - zone.get("r").asDouble();
            case "path" -> pointCoords(zone, "y").min().orElseThrow();
            default -> zone.get("y").asDouble();
        };
    }

    /** Bounding-box width for any shape ({@code w} for rects, {@code 2r} for circles, point spread for paths). */
    private double widthOf(JsonNode zone) {
        return switch (zone.path("shape").asText()) {
            case "circle" -> 2 * zone.get("r").asDouble();
            case "path" -> pointCoords(zone, "x").max().orElseThrow() - pointCoords(zone, "x").min().orElseThrow();
            default -> zone.get("w").asDouble();
        };
    }

    /** Bounding-box height for any shape ({@code h} for rects, {@code 2r} for circles, point spread for paths). */
    private double heightOf(JsonNode zone) {
        return switch (zone.path("shape").asText()) {
            case "circle" -> 2 * zone.get("r").asDouble();
            case "path" -> pointCoords(zone, "y").max().orElseThrow() - pointCoords(zone, "y").min().orElseThrow();
            default -> zone.get("h").asDouble();
        };
    }

    private java.util.stream.DoubleStream pointCoords(JsonNode zone, String field) {
        return StreamSupport.stream(zone.path("points").spliterator(), false)
                .mapToDouble(point -> point.get(field).asDouble());
    }
}
