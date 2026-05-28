package com.luckledger.api;

import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.mechanic.MechanicType;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory registry of the games available to play. Games are pre-generated at startup (there is no
 * live {@code POST /games}); the store hands them out read-only by id. Keyed by a generated game id.
 */
@Service
public class GameStore {

    private final Map<UUID, GameSetupResult> games = new ConcurrentHashMap<>();

    /**
     * Registers a pre-generated game under a fresh id.
     *
     * @param setup the generated/allocated game; never {@code null}
     * @return the assigned game id
     */
    public UUID register(GameSetupResult setup) {
        Objects.requireNonNull(setup, "setup must not be null");
        UUID gameId = UUID.randomUUID();
        games.put(gameId, setup);
        return gameId;
    }

    /**
     * @param gameId the game id; never {@code null}
     * @return the game
     * @throws NoSuchElementException if no game has that id
     */
    public GameSetupResult get(UUID gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        GameSetupResult setup = games.get(gameId);
        if (setup == null) {
            throw new NoSuchElementException("no game with id " + gameId);
        }
        return setup;
    }

    /** All registered game ids. */
    public Set<UUID> gameIds() {
        return Set.copyOf(games.keySet());
    }

    /** All registered games, keyed by id (immutable snapshot). */
    public Map<UUID, GameSetupResult> all() {
        return Map.copyOf(games);
    }

    /** The mechanic a game was generated with, read from its first ticket. */
    public static MechanicType mechanicOf(GameSetupResult setup) {
        return setup.generationResult().tickets().get(0).layout().mechanicType();
    }
}
