package com.luckledger.api;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.distribution.AllocationQuartile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only access to the NPC dealers across all seeded games. */
@RestController
@RequestMapping("/api/dealers")
public class DealerController {

    private final GameStore gameStore;

    public DealerController(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    @GetMapping
    public List<DealerDto> list() {
        Map<UUID, String> gameNames = gameStore.games().stream()
                .collect(Collectors.toMap(GameEntity::getId, DealerController::gameName));
        return gameStore.dealers().stream()
                .map(d -> dto(d, gameNames.getOrDefault(d.getGameId(), "Unknown game")))
                .toList();
    }

    @GetMapping("/{dealerId}")
    public DealerDto get(@PathVariable UUID dealerId) {
        DealerEntity dealer = gameStore.dealer(dealerId);
        return dto(dealer, gameName(gameStore.game(dealer.getGameId())));
    }

    private DealerDto dto(DealerEntity dealer, String gameName) {
        return new DealerDto(
                dealer.getId(),
                dealer.getName(),
                dealer.getGameId(),
                gameName,
                dealer.getTier().name(),
                AllocationQuartile.fromTier(dealer.getTier()).name(),
                gameStore.activeBookCount(dealer.getId()),
                dealer.getBooksDepleted());
    }

    /** Turns a mechanic enum (e.g. {@code CELESTIAL_FORTUNE}) into a display name ("Celestial Fortune"). */
    private static String gameName(GameEntity game) {
        String[] words = game.getMechanicType().name().toLowerCase().split("_");
        return java.util.Arrays.stream(words)
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    public record DealerDto(
            UUID dealerId, String name, UUID gameId, String gameName, String tier, String quartile,
            int activeBooks, int booksDepleted) {}
}
