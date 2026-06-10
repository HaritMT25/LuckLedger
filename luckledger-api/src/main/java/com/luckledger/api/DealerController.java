package com.luckledger.api;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.distribution.AllocationQuartile;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only access to the NPC shops (dealers) and the games each one stocks. */
@RestController
@RequestMapping("/api/dealers")
public class DealerController {

    private final GameStore gameStore;

    public DealerController(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    @GetMapping
    public List<DealerDto> list() {
        Map<UUID, String> gameNames = gameNameIndex();
        return gameStore.dealers().stream().map(d -> dto(d, gameNames)).toList();
    }

    @GetMapping("/{dealerId}")
    public DealerDto get(@PathVariable UUID dealerId) {
        return dto(gameStore.dealer(dealerId), gameNameIndex());
    }

    /** The books a shop stocks — the only way to reach books (there is no flat book catalogue). */
    @GetMapping("/{dealerId}/books")
    public List<BookController.BookDto> books(@PathVariable UUID dealerId) {
        gameStore.dealer(dealerId); // 404 if the shop does not exist
        Map<UUID, GameEntity> gamesById = gameStore.games().stream()
                .collect(Collectors.toMap(GameEntity::getId, g -> g));
        return gameStore.booksForDealer(dealerId).stream()
                .map(b -> BookController.toDto(b, gamesById.get(b.getGameId())))
                .toList();
    }

    private DealerDto dto(DealerEntity dealer, Map<UUID, String> gameNames) {
        List<GameRef> stocked = dealer.getStockedGames().stream()
                .map(id -> new GameRef(id, gameNames.getOrDefault(id, "Unknown game")))
                .toList();
        return new DealerDto(
                dealer.getId(),
                dealer.getShopName(),
                dealer.getOwnerName(),
                dealer.getAvatar(),
                stocked,
                dealer.getTier().name(),
                AllocationQuartile.fromTier(dealer.getTier()).name(),
                gameStore.activeBookCount(dealer.getId()),
                dealer.getBooksDepleted());
    }

    private Map<UUID, String> gameNameIndex() {
        return gameStore.games().stream()
                .collect(Collectors.toMap(GameEntity::getId, DealerController::gameName));
    }

    /** Turns a mechanic enum (e.g. {@code CELESTIAL_FORTUNE}) into a display name ("Celestial Fortune"). */
    static String gameName(GameEntity game) {
        return Arrays.stream(game.getMechanicType().name().toLowerCase().split("_"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    public record GameRef(UUID gameId, String gameName) {}

    public record DealerDto(
            UUID dealerId, String shopName, String ownerName, String avatar, List<GameRef> games,
            String tier, String quartile, int activeBooks, int booksDepleted) {}
}
