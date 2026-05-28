package com.luckledger.api;

import com.luckledger.domain.player.Player;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** In-memory store of players created through the API. */
@Service
public class PlayerRegistry {

    private final Map<UUID, Player> players = new ConcurrentHashMap<>();

    /** Creates and registers a new player with a zero balance. */
    public Player create(String displayName) {
        Player player = new Player(UUID.randomUUID(), displayName);
        players.put(player.getPlayerId(), player);
        return player;
    }

    public Player get(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Player player = players.get(playerId);
        if (player == null) {
            throw new NoSuchElementException("no player with id " + playerId);
        }
        return player;
    }
}
