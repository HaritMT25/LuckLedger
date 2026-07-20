package com.luckledger.api;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.GamePersistenceMapper;
import com.luckledger.api.persistence.GamePersistenceMapper.PersistedGame;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.PoolContractDoc;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.api.persistence.TicketRepository.BookTicketStats;
import com.luckledger.api.persistence.TicketRepository.GameTicketStats;
import com.luckledger.api.persistence.TransactionRepository;
import com.luckledger.api.persistence.TransactionRepository.ShopSales;
import com.luckledger.cli.GameOrchestrator;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.orchestration.GameConfig;
import com.luckledger.domain.orchestration.GameStatus;
import com.luckledger.domain.pool.BookProfile;
import com.luckledger.domain.pool.PoolContract;
import com.luckledger.domain.pool.PoolFactory;
import com.luckledger.domain.pool.PoolValidator;
import com.luckledger.domain.pool.PrizeTier;
import com.luckledger.domain.pool.ValidationResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The master's campaign control surface, behind the read/write boundary of {@link CampaignController}.
 * A campaign is an ordinary game created at runtime through the <em>same verified generation pipeline</em>
 * the two demo games use — mandatory verification, constructive population, identical persistence. There
 * is no shortcut and no separate table.
 *
 * <p><strong>RTP is sacred and server-derived.</strong> A campaign's return-to-player comes purely from
 * its tier structure ({@code Σ tierValue×count ÷ price×totalTickets}); the client never supplies it.
 * Once a campaign exists its economics are fixed — retuning is impossible by construction. The operator
 * can only stop selling it ({@link #setStatus retire}); doing so touches neither tickets nor the ledger,
 * so already-sold tickets still reveal and pay.
 */
@Service
public class CampaignService {

    /** Money is presented at 4 decimals; the derived RTP ratio at 6. */
    private static final int MONEY_SCALE = 4;
    private static final int RTP_SCALE = 6;

    private final PoolFactory poolFactory;
    private final PoolValidator poolValidator;
    private final GameOrchestrator celestialOrchestrator;
    private final GameOrchestrator demonOrchestrator;
    private final GameRepository games;
    private final DealerRepository dealers;
    private final TicketBookRepository books;
    private final TicketRepository tickets;
    private final TransactionRepository transactions;

    public CampaignService(
            PoolFactory poolFactory,
            PoolValidator poolValidator,
            @Qualifier("celestialOrchestrator") GameOrchestrator celestialOrchestrator,
            @Qualifier("demonOrchestrator") GameOrchestrator demonOrchestrator,
            GameRepository games,
            DealerRepository dealers,
            TicketBookRepository books,
            TicketRepository tickets,
            TransactionRepository transactions) {
        this.poolFactory = poolFactory;
        this.poolValidator = poolValidator;
        this.celestialOrchestrator = celestialOrchestrator;
        this.demonOrchestrator = demonOrchestrator;
        this.games = games;
        this.dealers = dealers;
        this.books = books;
        this.tickets = tickets;
        this.transactions = transactions;
    }

    /**
     * Lists every campaign (game) with its lifecycle and headline sales, newest economics visible at a
     * glance for the dashboard.
     *
     * @return one summary per persisted game
     */
    @Transactional(readOnly = true)
    public List<CampaignSummary> list() {
        Map<UUID, GameTicketStats> statsByGame = tickets.aggregateByGame().stream()
                .collect(Collectors.toMap(GameTicketStats::getGameId, Function.identity()));
        return games.findAll().stream().map(g -> summary(g, statsByGame.get(g.getId()))).toList();
    }

    /**
     * Computes what a campaign <em>would</em> be without creating it: the server-derived RTP, winner
     * count, prize budget, win frequency, and whether the pool is economically valid (with every
     * validation error collected, never thrown). Persists nothing.
     *
     * @param request the campaign to preview; never {@code null}
     * @return the preview
     */
    @Transactional(readOnly = true)
    public CampaignPreview preview(CreateCampaignRequest request) {
        Objects.requireNonNull(request, "request");
        BigDecimal revenue = revenue(request.price(), request.totalTickets());
        BigDecimal tierCost = tierCost(request.tiers());
        int winnerCount = request.tiers().stream().mapToInt(CreateCampaignRequest.TierSpec::count).sum();

        List<String> errors = new ArrayList<>();
        boolean valid;
        try {
            ValidationResult result = poolValidator.validate(buildPool(request));
            valid = result.isValid();
            errors.addAll(result.errors());
        } catch (RuntimeException ex) {
            // A tier that cannot even be constructed (e.g. a non-positive prize) is reported, not thrown.
            valid = false;
            errors.add(ex.getMessage());
        }

        return new CampaignPreview(
                designedRtp(tierCost, revenue),
                winnerCount,
                tierCost.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                winFrequency(winnerCount, request.totalTickets()),
                valid,
                errors);
    }

    /**
     * Creates a campaign: assembles and validates the pool (invalid → 422 via the pool factory), runs it
     * through the verified generation pipeline, allocates its books to the chosen shops, and persists the
     * whole graph — game (named, ACTIVE, carrying its exact pool contract), books, and tickets — then
     * stocks the campaign in each chosen shop.
     *
     * @param request the campaign to create; never {@code null}
     * @return the created campaign's summary
     * @throws com.luckledger.domain.pool.InvalidPoolException if the tiers do not form a valid pool (422)
     * @throws IllegalArgumentException if the mechanic is not one of the shipped mechanics (422)
     * @throws NoSuchElementException if any supplied shop id does not exist (404)
     */
    @Transactional
    public CampaignSummary create(CreateCampaignRequest request) {
        Objects.requireNonNull(request, "request");

        // Assemble + validate the pool. An economically invalid pool throws InvalidPoolException here.
        PoolContract pool = poolFactory.create(builder(request));

        Mechanic mechanic = mechanicFor(request.mechanicType());

        // Every shop must exist before we generate — an unknown shop is a 404, not a half-built game.
        List<DealerEntity> shops = new ArrayList<>(request.shopIds().size());
        for (UUID shopId : request.shopIds()) {
            shops.add(dealers.findById(shopId)
                    .orElseThrow(() -> new NoSuchElementException("no shop with id " + shopId)));
        }

        GameConfig config = new GameConfig(
                pool, request.mechanicType(), mechanic.themeId(), request.books(), shops.size(),
                request.nearMissMode(), request.bookVisibility());

        // Same verified pipeline as the seed games; allocate to the chosen shops' slots.
        GameSetupResult setup = mechanic.orchestrator().setup(config, GameSeeder.dealerSlots(shops));

        UUID gameId = UUID.randomUUID();
        PoolContractDoc doc = PoolContractDoc.fromDomain(pool);
        PersistedGame persisted = GamePersistenceMapper.toPersisted(
                gameId, request.name(), config, setup, Instant.now(), doc,
                index -> config.bookMetadataVisibility());
        games.save(persisted.game());
        books.saveAll(persisted.books());
        tickets.saveAll(persisted.tickets());

        // Stock the new campaign in each chosen shop so its allocated books are reachable.
        for (DealerEntity shop : shops) {
            shop.addStockedGame(gameId);
            dealers.save(shop);
        }

        return summary(persisted.game(), null);
    }

    /**
     * Flips a campaign's lifecycle status. Retiring stops new purchases and blocks restock; it never
     * touches sold tickets or the ledger, so outstanding tickets still reveal and pay. Activating a
     * retired campaign restores purchasing.
     *
     * @param gameId the campaign; never {@code null}
     * @param status the new status; never {@code null}
     * @return the updated summary
     * @throws NoSuchElementException if the campaign does not exist (404)
     */
    @Transactional
    public CampaignSummary setStatus(UUID gameId, GameStatus status) {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(status, "status");
        GameEntity game = games.findById(gameId)
                .orElseThrow(() -> new NoSuchElementException("no game with id " + gameId));
        game.setStatus(status);
        games.save(game);
        GameTicketStats stats = tickets.aggregateByGame().stream()
                .filter(s -> s.getGameId().equals(gameId)).findFirst().orElse(null);
        return summary(game, stats);
    }

    /**
     * The full analytics for one campaign, reconciling its fixed design against what has actually
     * happened on the ledger: designed vs realized RTP, ticket flow, gross sales / payouts / house net,
     * and per-shop and per-book breakdowns.
     *
     * @param gameId the campaign; never {@code null}
     * @return the analytics
     * @throws NoSuchElementException if the campaign does not exist (404)
     */
    @Transactional(readOnly = true)
    public CampaignAnalytics analytics(UUID gameId) {
        Objects.requireNonNull(gameId, "gameId");
        GameEntity game = games.findById(gameId)
                .orElseThrow(() -> new NoSuchElementException("no game with id " + gameId));

        GameTicketStats gs = tickets.aggregateByGame().stream()
                .filter(s -> s.getGameId().equals(gameId)).findFirst().orElse(null);
        long total = gs == null ? game.getTotalTickets() : gs.getTotalTickets();
        long sold = gs == null ? 0L : gs.getSoldTickets();
        long revealed = gs == null ? 0L : gs.getRevealedTickets();

        List<ShopSales> sales = transactions.salesByShopForGame(gameId);
        BigDecimal grossSales = sales.stream()
                .map(ShopSales::getGrossSales).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidOut = sales.stream()
                .map(ShopSales::getPaidOut).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal realizedRtp = grossSales.signum() == 0
                ? null : paidOut.divide(grossSales, RTP_SCALE, RoundingMode.HALF_UP);

        Map<UUID, String> shopNames = dealers.findAllById(sales.stream()
                        .map(ShopSales::getShopId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(DealerEntity::getId, DealerEntity::getShopName));
        List<ShopBreakdown> shopBreakdowns = sales.stream()
                .map(s -> new ShopBreakdown(
                        s.getShopId(),
                        shopNames.getOrDefault(s.getShopId(), "Unknown shop"),
                        s.getTicketsSold(),
                        money(s.getGrossSales()),
                        money(s.getPaidOut()),
                        money(s.getGrossSales().subtract(s.getPaidOut()))))
                .toList();

        List<TicketBookEntity> gameBooks = books.findByGameId(gameId);
        Map<UUID, BookTicketStats> bookStats = gameBooks.isEmpty() ? Map.of()
                : tickets.aggregateByBook(gameBooks.stream().map(TicketBookEntity::getId).toList()).stream()
                        .collect(Collectors.toMap(BookTicketStats::getBookId, Function.identity()));
        List<BookDepletion> bookBreakdowns = gameBooks.stream().map(b -> {
            BookTicketStats bs = bookStats.get(b.getId());
            BigDecimal prizeFund = bs == null ? BigDecimal.ZERO : bs.getPrizeFund();
            BigDecimal dispensed = bs == null ? BigDecimal.ZERO : bs.getDispensed();
            long wins = bs == null ? 0L : bs.getWinsSoFar();
            return new BookDepletion(
                    b.getId(), b.getDealerId(), b.getMetadataVisibility().name(),
                    b.getTotalTickets(), b.getNextIndex(), b.getTotalTickets() - b.getNextIndex(),
                    money(prizeFund), money(dispensed), money(prizeFund.subtract(dispensed)), wins);
        }).toList();

        PoolContractDoc doc = game.getPoolContract();
        BigDecimal designedRtp = doc == null ? null
                : designedRtp(tierCostFromDoc(doc), revenue(doc.ticketPrice(), doc.totalTickets()));

        return new CampaignAnalytics(
                game.getId(),
                game.getName(),
                game.getMechanicType().name(),
                game.getStatus().name(),
                money(game.getTicketPrice()),
                designedRtp,
                realizedRtp,
                total,
                sold,
                total - sold,
                revealed,
                money(grossSales),
                money(paidOut),
                money(grossSales.subtract(paidOut)),
                game.getNearMissMode().name(),
                game.getNearMiss().nearMissRate(),
                shopBreakdowns,
                bookBreakdowns);
    }

    // --- internals -----------------------------------------------------------

    private CampaignSummary summary(GameEntity game, GameTicketStats stats) {
        long total = stats == null ? game.getTotalTickets() : stats.getTotalTickets();
        long sold = stats == null ? 0L : stats.getSoldTickets();
        return new CampaignSummary(
                game.getId(),
                game.getName(),
                game.getMechanicType().name(),
                game.getStatus().name(),
                money(game.getTicketPrice()),
                sold,
                total);
    }

    private PoolContract buildPool(CreateCampaignRequest request) {
        return builder(request).build();
    }

    /** Assembles the pool builder, deriving the payout ratio from the tiers (RTP is never an input). */
    private PoolContract.Builder builder(CreateCampaignRequest request) {
        BigDecimal revenue = revenue(request.price(), request.totalTickets());
        BigDecimal tierCost = tierCost(request.tiers());
        // A high-precision ratio so the validator's budget-balance check (revenue×ratio ≈ tierCost) holds.
        BigDecimal payoutRatio = revenue.signum() == 0
                ? BigDecimal.ZERO : tierCost.divide(revenue, 10, RoundingMode.HALF_UP);
        PoolContract.Builder builder = PoolContract.builder()
                .totalTickets(request.totalTickets())
                .ticketPrice(request.price())
                .payoutRatio(payoutRatio)
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED);
        request.tiers().forEach(t -> builder.addPrizeTier(new PrizeTier(t.value(), t.count(), t.label())));
        return builder;
    }

    private static BigDecimal revenue(BigDecimal price, int totalTickets) {
        return price.multiply(BigDecimal.valueOf(totalTickets));
    }

    private static BigDecimal tierCost(List<CreateCampaignRequest.TierSpec> tiers) {
        return tiers.stream()
                .map(t -> t.value().multiply(BigDecimal.valueOf(t.count())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal tierCostFromDoc(PoolContractDoc doc) {
        return doc.prizeTiers().stream()
                .map(t -> t.value().multiply(BigDecimal.valueOf(t.count())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The server-derived RTP: Σ(tierValue×count) ÷ (price×totalTickets), scale 6. */
    private static BigDecimal designedRtp(BigDecimal tierCost, BigDecimal revenue) {
        if (revenue.signum() == 0) {
            return BigDecimal.ZERO.setScale(RTP_SCALE);
        }
        return tierCost.divide(revenue, RTP_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal winFrequency(int winnerCount, int totalTickets) {
        if (totalTickets == 0) {
            return BigDecimal.ZERO.setScale(RTP_SCALE);
        }
        return BigDecimal.valueOf(winnerCount).divide(BigDecimal.valueOf(totalTickets), RTP_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private Mechanic mechanicFor(MechanicType type) {
        return switch (type) {
            case CELESTIAL_FORTUNE -> new Mechanic(celestialOrchestrator, ApiConfig.CELESTIAL_THEME_ID);
            case DEMON_SEAL -> new Mechanic(demonOrchestrator, ApiConfig.DEMON_THEME_ID);
            default -> throw new IllegalArgumentException(
                    "unsupported mechanic for a campaign: " + type + " (choose CELESTIAL_FORTUNE or DEMON_SEAL)");
        };
    }

    private record Mechanic(GameOrchestrator orchestrator, String themeId) {}

    // --- response shapes -----------------------------------------------------

    /** One row of the campaign list. */
    public record CampaignSummary(
            UUID gameId, String name, String mechanic, String status, BigDecimal ticketPrice,
            long sold, long total) {}

    /**
     * The result of previewing a campaign without creating it.
     *
     * @param designedRtp server-derived RTP (scale 6): Σ(tierValue×count) ÷ (price×totalTickets)
     * @param winnerCount total winning tickets across all tiers
     * @param prizeBudget the coins all winning tiers together pay (Σ tierValue×count), scale 4
     * @param winFrequency fraction of tickets that win (scale 6)
     * @param valid whether the assembled pool is economically valid
     * @param errors every validation error (empty when {@code valid})
     */
    public record CampaignPreview(
            BigDecimal designedRtp, int winnerCount, BigDecimal prizeBudget, BigDecimal winFrequency,
            boolean valid, List<String> errors) {}

    /** One shop's contribution to a campaign's realized numbers. */
    public record ShopBreakdown(
            UUID shopId, String shopName, long ticketsSold, BigDecimal grossSales, BigDecimal paidOut,
            BigDecimal netToHouse) {}

    /** One book's depletion state within a campaign. */
    public record BookDepletion(
            UUID bookId, UUID shopId, String visibility, int totalTickets, int sold, int remaining,
            BigDecimal prizeFund, BigDecimal dispensed, BigDecimal remainingValue, long winsSoFar) {}

    /**
     * The full analytics for a campaign.
     *
     * @param designedRtp the RTP fixed at creation (scale 6); null for legacy games with no stored contract
     * @param realizedRtp paidOut ÷ grossSales (scale 6); null until at least one sale has happened
     * @param engineeredNearMissRate the manufactured near-miss rate in the generated pool
     */
    public record CampaignAnalytics(
            UUID gameId, String name, String mechanic, String status, BigDecimal ticketPrice,
            BigDecimal designedRtp, BigDecimal realizedRtp, long totalTickets, long sold, long remaining,
            long revealed, BigDecimal grossSales, BigDecimal paidOut, BigDecimal houseNet,
            String nearMissMode, double engineeredNearMissRate, List<ShopBreakdown> shops,
            List<BookDepletion> books) {}
}
