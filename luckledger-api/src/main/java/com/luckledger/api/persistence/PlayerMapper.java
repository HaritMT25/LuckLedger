package com.luckledger.api.persistence;

import com.luckledger.domain.player.Player;
import java.time.Instant;

/** Maps between the domain {@link Player} and its JPA {@link PlayerEntity}. */
public final class PlayerMapper {

    private PlayerMapper() {}

    /** A brand-new entity row for a freshly created player. */
    public static PlayerEntity newEntity(Player player, Instant createdAt) {
        return new PlayerEntity(
                player.getPlayerId(),
                player.getDisplayName(),
                player.getCoinBalance(),
                player.getTotalBorrowed(),
                player.getTotalSpent(),
                player.getTotalWon(),
                player.getTicketCount(),
                createdAt);
    }

    /** Reconstructs the domain {@link Player} (with its running totals) from a stored row. */
    public static Player toDomain(PlayerEntity e) {
        return Player.rehydrate(
                e.getId(),
                e.getDisplayName(),
                e.getCoinBalance(),
                e.getTotalBorrowed(),
                e.getTotalSpent(),
                e.getTotalWon(),
                e.getTicketCount());
    }

    /** Copies a (mutated) domain player's bankroll state back onto its entity for persisting. */
    public static void applyTo(Player player, PlayerEntity e) {
        e.setCoinBalance(player.getCoinBalance());
        e.setTotalBorrowed(player.getTotalBorrowed());
        e.setTotalSpent(player.getTotalSpent());
        e.setTotalWon(player.getTotalWon());
        e.setTicketCount(player.getTicketCount());
    }
}
