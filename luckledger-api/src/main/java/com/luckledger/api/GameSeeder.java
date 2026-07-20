package com.luckledger.api;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.api.persistence.GamePersistenceMapper;
import com.luckledger.api.persistence.GamePersistenceMapper.PersistedGame;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.PoolContractDoc;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.cli.GameOrchestrator;
import com.luckledger.distribution.Dealer;
import com.luckledger.distribution.DealerTier;
import com.luckledger.distribution.DealerTierResolver;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.generation.MetadataVisibility;
import com.luckledger.domain.orchestration.GameConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the two demo games (Celestial Fortune and Demon Seal) to Postgres at startup, along with a
 * fixed roster of NPC <em>shops</em>. Shops are storefronts with a human owner, and each stocks a
 * chosen subset of the games — so the same shop (e.g. Lucky Mart) can sell both games, while another
 * (QuickStop) sells only one. Each game's books are allocated only to the shops that stock it; the
 * generation, ranking, and RTP rules are unchanged.
 *
 * <p>Idempotent: if any game is already persisted (e.g. a restart against a populated database) it does
 * nothing, so the demo set is never duplicated.
 */
@Component
public class GameSeeder implements ApplicationRunner {

    private static final int BOOKS_PER_CYCLE = 50;

    /**
     * The visibility tiers cycled across a game's books (index % 3) so the demo shows all three at once.
     * The rotation is deterministic and purely a presentation device — every book of a game still has
     * identical per-ticket odds; only how much depletion history is shown differs.
     */
    private static final MetadataVisibility[] VISIBILITY_ROTATION = {
            MetadataVisibility.NONE, MetadataVisibility.PARTIAL, MetadataVisibility.FULL};

    private final GameOrchestrator celestialOrchestrator;
    private final GameOrchestrator demonOrchestrator;
    private final GameRepository games;
    private final DealerRepository dealers;
    private final TicketBookRepository books;
    private final TicketRepository tickets;

    public GameSeeder(
            @Qualifier("celestialOrchestrator") GameOrchestrator celestialOrchestrator,
            @Qualifier("demonOrchestrator") GameOrchestrator demonOrchestrator,
            GameRepository games,
            DealerRepository dealers,
            TicketBookRepository books,
            TicketRepository tickets) {
        this.celestialOrchestrator = celestialOrchestrator;
        this.demonOrchestrator = demonOrchestrator;
        this.games = games;
        this.dealers = dealers;
        this.books = books;
        this.tickets = tickets;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (games.count() > 0) {
            return; // already seeded
        }

        UUID celestialId = UUID.randomUUID();
        UUID demonId = UUID.randomUUID();

        // The demo shop roster. gameFilter decides which games each shop stocks. The seeded
        // booksDepleted gives each shop a sales history so its tier (§3.6) differs: allocation bands
        // books by dealer tier (LOWER/MIDDLE/UPPER), so a game's stocking shops must span the tiers for
        // every book to find a home. Each game's trio below covers all three tiers.
        DealerEntity luckyMart = shop("Lucky Mart", "Sam", List.of(celestialId, demonId), 60);     // TIER_3
        DealerEntity sevenStar = shop("7 Star Corner", "Priya", List.of(celestialId), 20);          // TIER_2
        DealerEntity goldenExpress = shop("Golden Express", "Old Chen", List.of(celestialId, demonId), 0); // TIER_1
        DealerEntity quickStop = shop("QuickStop", "Danny", List.of(demonId), 20);                  // TIER_2
        DealerEntity moonlight = shop("Moonlight Bodega", "Rosa", List.of(celestialId, demonId), 60); // TIER_3
        DealerEntity nightOwl = shop("Night Owl Mart", "Theo", List.of(demonId), 0);                // TIER_1
        List<DealerEntity> roster = List.of(luckyMart, sevenStar, goldenExpress, quickStop, moonlight, nightOwl);
        dealers.saveAll(roster);

        seed(ApiConfig.celestialConfig(), celestialOrchestrator, celestialId, "Celestial Fortune",
                stockedBy(roster, celestialId));
        seed(ApiConfig.demonConfig(), demonOrchestrator, demonId, "Demon Seal",
                stockedBy(roster, demonId));
    }

    /**
     * Maps persisted shops to domain {@link Dealer} allocation slots. The Dealer carries the shop's
     * stable id so each allocated book's dealerId points back at the persisted shop. Shared with
     * {@link RestockService}, which runs the same allocation on later cycles.
     */
    static List<Dealer> dealerSlots(List<DealerEntity> shops) {
        List<Dealer> slots = new ArrayList<>(shops.size());
        for (DealerEntity shop : shops) {
            slots.add(new Dealer(
                    shop.getId(), shop.getShopName(), DealerTier.TIER_1, shop.getRankScore(),
                    shop.getBooksPerCycle(), shop.getBooksDepleted()));
        }
        return slots;
    }

    private void seed(GameConfig config, GameOrchestrator orchestrator, UUID gameId, String name,
            List<DealerEntity> stockingShops) {
        GameSetupResult setup = orchestrator.setup(config, dealerSlots(stockingShops));
        // Stamp the demo game with its display name, ACTIVE status, and the exact pool contract it was
        // built from (so a restock regenerates identical economics). The mode (REALISTIC) rides along
        // via the config. Nothing else about the seed changes.
        PoolContractDoc contract = PoolContractDoc.fromDomain(config.poolContract());
        PersistedGame persisted = GamePersistenceMapper.toPersisted(
                gameId, name, config, setup, Instant.now(), contract, GameSeeder::rotatedVisibility);
        games.save(persisted.game());
        books.saveAll(persisted.books());
        tickets.saveAll(persisted.tickets());
    }

    /**
     * The visibility tier for the book at {@code bookIndex}, rotating NONE → PARTIAL → FULL. Shared with
     * {@link RestockService} so freshly restocked books get the same rotation.
     */
    static MetadataVisibility rotatedVisibility(int bookIndex) {
        return VISIBILITY_ROTATION[Math.floorMod(bookIndex, VISIBILITY_ROTATION.length)];
    }

    private static DealerEntity shop(String shopName, String ownerName, List<UUID> stockedGames, int booksDepleted) {
        // Persisted tier is informational; the allocator re-derives each domain dealer's tier from
        // booksDepleted at allocation time. We store the matching tier here so the API reports it too.
        DealerTier tier = new DealerTierResolver().resolve(
                new Dealer(UUID.randomUUID(), shopName, DealerTier.TIER_1, 0, BOOKS_PER_CYCLE, booksDepleted));
        return new DealerEntity(
                UUID.randomUUID(), shopName, ownerName, null, stockedGames,
                tier, 0, BOOKS_PER_CYCLE, booksDepleted);
    }

    private static List<DealerEntity> stockedBy(List<DealerEntity> roster, UUID gameId) {
        return roster.stream().filter(s -> s.getStockedGames().contains(gameId)).toList();
    }
}
