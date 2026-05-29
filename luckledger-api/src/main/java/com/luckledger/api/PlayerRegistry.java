package com.luckledger.api;

import com.luckledger.api.persistence.PlayerEntity;
import com.luckledger.api.persistence.PlayerMapper;
import com.luckledger.api.persistence.PlayerRepository;
import com.luckledger.domain.player.Player;
import com.luckledger.player.bank.BankService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Postgres-backed store of players. Replaces the former in-memory map: players are persisted as
 * {@link PlayerEntity} rows and reconstructed into domain {@link Player}s for the pure bankroll logic.
 *
 * <p>{@link #borrow} runs the {@link BankService} (which mutates the player and appends a {@code BORROW}
 * to the ledger) inside a single transaction, then writes the updated bankroll back, so the loan and
 * its balance change commit atomically.
 */
@Service
public class PlayerRegistry {

    private final PlayerRepository players;
    private final BankService bankService;

    public PlayerRegistry(PlayerRepository players, BankService bankService) {
        this.players = Objects.requireNonNull(players, "players must not be null");
        this.bankService = Objects.requireNonNull(bankService, "bankService must not be null");
    }

    /** Creates and persists a new player with a zero balance. */
    @Transactional
    public Player create(String displayName) {
        Player player = new Player(UUID.randomUUID(), displayName);
        players.save(PlayerMapper.newEntity(player, Instant.now()));
        return player;
    }

    /** Loads a player by id, reconstructing its running totals. */
    @Transactional(readOnly = true)
    public Player get(UUID playerId) {
        return PlayerMapper.toDomain(load(playerId));
    }

    /**
     * Grants the player a free loan and persists the new balance atomically.
     *
     * @return the player after borrowing
     */
    @Transactional
    public Player borrow(UUID playerId, BigDecimal amount) {
        PlayerEntity entity = load(playerId);
        Player player = PlayerMapper.toDomain(entity);
        bankService.borrow(player, amount); // mutates player + appends BORROW to the ledger
        PlayerMapper.applyTo(player, entity);
        players.save(entity);
        return player;
    }

    private PlayerEntity load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return players.findById(playerId)
                .orElseThrow(() -> new NoSuchElementException("no player with id " + playerId));
    }
}
