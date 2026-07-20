package com.luckledger.api;

import com.luckledger.domain.generation.MetadataVisibility;
import com.luckledger.domain.generation.NearMissMode;
import com.luckledger.domain.mechanic.MechanicType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The master's request to create (or preview) a campaign. Every field is validated at the boundary so a
 * structurally bad request is rejected with a 400 {@code VALIDATION_ERROR} before any generation runs;
 * economic invalidity that only shows up once the tiers are assembled (e.g. tiers that overspend the
 * revenue) is caught downstream by the pool validator as a 422.
 *
 * <p><strong>RTP is not an input.</strong> There is deliberately no payout-ratio field: a campaign's
 * return-to-player is <em>derived</em> from its tier structure (Σ tierValue×count ÷ price×totalTickets),
 * server-side. Nothing the client sends can set it directly, and it can never be retuned in place.
 *
 * @param name the campaign display name; non-blank, at most 120 characters
 * @param mechanicType the mechanic to generate with; required (only the shipped mechanics are supported)
 * @param price the ticket price; positive, at most 4 decimal places
 * @param tiers the winning prize tiers; 1–10 of them, each individually valid
 * @param totalTickets the pool size; 50–20000
 * @param books how many books to partition the pool into; 1–500
 * @param nearMissMode whether losers are engineered into near-misses (RTP-neutral); required
 * @param bookVisibility how much book depletion state to reveal to players (UI/data only); required
 * @param shopIds the shops that will stock this campaign's books; at least one
 */
public record CreateCampaignRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull MechanicType mechanicType,
        @NotNull @Positive @Digits(integer = 6, fraction = 4) BigDecimal price,
        @NotEmpty @Size(min = 1, max = 10) @Valid List<TierSpec> tiers,
        @Min(50) @Max(20000) int totalTickets,
        @Min(1) @Max(500) int books,
        @NotNull NearMissMode nearMissMode,
        @NotNull MetadataVisibility bookVisibility,
        @NotEmpty List<UUID> shopIds) {

    /**
     * A single winning tier in a campaign request.
     *
     * @param value the prize amount per ticket; at least 0, at most 4 decimal places (a 0 value is a
     *     structural pass but is rejected downstream — a prize tier must pay something)
     * @param count how many tickets win this tier; positive
     * @param label a human-readable name; non-blank
     */
    public record TierSpec(
            @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 4) BigDecimal value,
            @Positive int count,
            @NotBlank String label) {}
}
