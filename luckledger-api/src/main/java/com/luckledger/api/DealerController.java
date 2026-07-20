package com.luckledger.api;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketRepository.BookTicketStats;
import com.luckledger.distribution.AllocationQuartile;
import com.luckledger.domain.orchestration.GameStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

    /**
     * Lists every shop with its stocked games, tier, quartile, and book counts.
     *
     * @return all shops
     */
    @GetMapping
    public List<DealerDto> list() {
        Map<UUID, String> gameNames = gameNameIndex();
        return gameStore.dealers().stream().map(d -> dto(d, gameNames)).toList();
    }

    /**
     * The shop leaderboard: shops ranked by how many books they have sold out over their lifetime.
     *
     * <p><strong>Education point.</strong> A high rank does not mean a shop is "luckier" or "hotter".
     * Every book of a game has identical per-ticket odds no matter where it is sold; a shop near the top
     * simply moved more tickets. Ranking by {@code booksDepleted} makes the debunk concrete — throughput,
     * not luck, is all this board measures.
     *
     * <p>Declared before {@code /{dealerId}} and matched by its literal path, so it is never captured by
     * the id path variable (which would otherwise reject the word "rankings" as a malformed UUID).
     *
     * @return shops ordered by books depleted (descending), ties broken by shop name; ranks start at 1
     */
    @GetMapping("/rankings")
    public List<ShopRanking> rankings() {
        List<DealerEntity> sorted = gameStore.dealers().stream()
                .sorted(Comparator.comparingInt(DealerEntity::getBooksDepleted).reversed()
                        .thenComparing(DealerEntity::getShopName))
                .toList();
        List<ShopRanking> ranked = new ArrayList<>(sorted.size());
        int rank = 1;
        for (DealerEntity d : sorted) {
            ranked.add(new ShopRanking(
                    rank++, d.getId(), d.getShopName(), d.getOwnerName(), d.getTier().name(),
                    AllocationQuartile.fromTier(d.getTier()).name(), d.getBooksDepleted(),
                    gameStore.activeBookCount(d.getId())));
        }
        return ranked;
    }

    /**
     * A single shop by id.
     *
     * @param dealerId the shop's id
     * @return the shop
     */
    @GetMapping("/{dealerId}")
    public DealerDto get(@PathVariable UUID dealerId) {
        return dto(gameStore.dealer(dealerId), gameNameIndex());
    }

    /**
     * The books a shop stocks — the only way to reach books (there is no flat book catalogue). Each
     * book's depletion data is gated server-side by its visibility tier (see {@link BookController}).
     *
     * @param dealerId the shop's id
     * @return the shop's books as visibility-appropriate metadata DTOs
     */
    @GetMapping("/{dealerId}/books")
    public List<BookController.BookDto> books(@PathVariable UUID dealerId) {
        gameStore.dealer(dealerId); // 404 if the shop does not exist
        Map<UUID, GameEntity> gamesById = gameStore.games().stream()
                .collect(Collectors.toMap(GameEntity::getId, g -> g));
        // A retired campaign's books are off the shelf: hide them from the shop's shopfront listing.
        List<TicketBookEntity> shopBooks = gameStore.booksForDealer(dealerId).stream()
                .filter(b -> {
                    GameEntity game = gamesById.get(b.getGameId());
                    return game != null && game.getStatus() != GameStatus.RETIRED;
                })
                .toList();
        Map<UUID, BookTicketStats> stats = gameStore.bookStats(
                shopBooks.stream().map(TicketBookEntity::getId).toList());
        return shopBooks.stream()
                .map(b -> BookController.toDto(b, gamesById.get(b.getGameId()), stats.get(b.getId())))
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

    /**
     * One row of the shop leaderboard.
     *
     * @param rank 1-based position after sorting by books depleted (descending), shop name breaking ties
     * @param dealerId the shop's id
     * @param shopName the shop's display name
     * @param ownerName the shop owner's name
     * @param tier the shop's distribution tier
     * @param quartile the allocation band the tier maps to
     * @param booksDepleted lifetime books sold out — the only quantity this board ranks on
     * @param activeBooks books still selling right now
     */
    public record ShopRanking(
            int rank, UUID dealerId, String shopName, String ownerName, String tier, String quartile,
            int booksDepleted, int activeBooks) {}
}
