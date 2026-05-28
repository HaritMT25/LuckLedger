package com.luckledger.domain.generation;

import com.luckledger.domain.generation.theme.ThemedGrid;
import com.luckledger.domain.generation.theme.ThemeRef;
import java.util.Objects;
import java.util.UUID;

/**
 * Layer 4 of the generation pipeline: the final, fully renderable ticket. It wraps the abstract
 * {@link TicketLayout} with its skinned grid and the {@link ThemeRef} that produced the visuals — a
 * {@code TicketCard} is everything the frontend needs to draw and play a ticket.
 *
 * @param ticketId the ticket's public id; never {@code null}
 * @param layout the underlying abstract layout (outcome + mechanic grid); never {@code null}
 * @param skinnedGrid the themed grid rendered from the layout; never {@code null}
 * @param theme the theme applied; never {@code null}
 */
public record TicketCard(
        UUID ticketId, TicketLayout layout, ThemedGrid skinnedGrid, ThemeRef theme) {

    public TicketCard {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(layout, "layout must not be null");
        Objects.requireNonNull(skinnedGrid, "skinnedGrid must not be null");
        Objects.requireNonNull(theme, "theme must not be null");
    }
}
