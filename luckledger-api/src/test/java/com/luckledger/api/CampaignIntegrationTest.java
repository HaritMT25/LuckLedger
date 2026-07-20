package com.luckledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.PlayerEntity;
import com.luckledger.api.persistence.PlayerRepository;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.distribution.DealerTier;
import com.luckledger.domain.orchestration.GameStatus;
import com.luckledger.domain.scratch.TicketStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage of the master campaign control surface against a real Postgres (Testcontainers):
 * create through the verified pipeline, preview (persisting nothing), analytics reconciled against the
 * ledger, the retire/activate lifecycle, campaign-aware restock, and the master gate. {@code @Transactional}
 * rolls back each test. Dependencies are constructor-injected (SpringExtension resolves the parameters).
 */
@SpringBootTest(classes = {
        TestApplication.class,
        ApiConfig.class,
        GameStore.class,
        PlayerRegistry.class,
        PurchaseGateway.class,
        RevealGateway.class,
        RevealNarrator.class,
        CampaignController.class,
        CampaignService.class,
        GameController.class,
        BookController.class,
        DealerController.class,
        TicketController.class,
        PlayerController.class,
        HouseController.class,
        SecurityConfig.class,
        AuthController.class,
        MasterController.class,
        RestockService.class,
        GlobalExceptionHandler.class
}, properties = {
        "luckledger.master.username=master",
        "luckledger.master.password=test-password"
})
@AutoConfigureMockMvc
@Transactional
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CampaignIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private final MockMvc mockMvc;
    private final GameRepository games;
    private final DealerRepository dealers;
    private final TicketBookRepository books;
    private final TicketRepository tickets;
    private final PlayerRepository players;

    CampaignIntegrationTest(MockMvc mockMvc, GameRepository games, DealerRepository dealers,
            TicketBookRepository books, TicketRepository tickets, PlayerRepository players) {
        this.mockMvc = mockMvc;
        this.games = games;
        this.dealers = dealers;
        this.books = books;
        this.tickets = tickets;
        this.players = players;
    }

    private UUID shopLower;
    private UUID shopMiddle;
    private UUID shopUpper;

    @BeforeEach
    void seedShops() {
        // Three shops spanning the allocation tiers (LOWER/MIDDLE/UPPER) so every generated book finds
        // a home. Tier is resolved from booksDepleted: <10 TIER_1, <50 TIER_2, else TIER_3.
        shopLower = shop("Lower Mart", 0, DealerTier.TIER_1);
        shopMiddle = shop("Middle Mart", 20, DealerTier.TIER_2);
        shopUpper = shop("Upper Mart", 60, DealerTier.TIER_3);
    }

    private UUID shop(String name, int booksDepleted, DealerTier tier) {
        UUID id = UUID.randomUUID();
        dealers.save(new DealerEntity(id, name, "Owner", null, List.of(), tier, 0, 50, booksDepleted));
        return id;
    }

    private RequestPostProcessor master() {
        return user("master").roles("MASTER");
    }

    /** A valid Demon Seal campaign: 120 tickets @ 5, tiers on the ladder (10×12, 2×24) → RTP 168/600 = 0.28. */
    private String validRequest() {
        return """
                {
                  "name": "Test Campaign",
                  "mechanicType": "DEMON_SEAL",
                  "price": 5,
                  "tiers": [
                    {"value": 10, "count": 12, "label": "Mid"},
                    {"value": 2, "count": 24, "label": "Small"}
                  ],
                  "totalTickets": 120,
                  "books": 6,
                  "nearMissMode": "REALISTIC",
                  "bookVisibility": "PARTIAL",
                  "shopIds": ["%s", "%s", "%s"]
                }
                """.formatted(shopLower, shopMiddle, shopUpper);
    }

    private UUID fundedPlayer() {
        UUID id = UUID.randomUUID();
        players.save(new PlayerEntity(
                id, "Player", new BigDecimal("1000"), new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, 0, Instant.now()));
        return id;
    }

    private UUID createCampaign() throws Exception {
        String created = mockMvc.perform(post("/api/master/campaigns")
                        .with(master()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(created, "$.gameId"));
    }

    private UUID allocatedBook(UUID gameId) {
        return books.findByGameId(gameId).stream()
                .filter(b -> b.getDealerId() != null)
                .map(TicketBookEntity::getId)
                .findFirst().orElseThrow();
    }

    // --- create --------------------------------------------------------------

    @Test
    void createsACampaignThroughTheVerifiedPipeline() throws Exception {
        String created = mockMvc.perform(post("/api/master/campaigns")
                        .with(master()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Campaign"))
                .andExpect(jsonPath("$.mechanic").value("DEMON_SEAL"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.total").value(120))
                .andExpect(jsonPath("$.sold").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID gameId = UUID.fromString(JsonPath.read(created, "$.gameId"));

        // Persisted shape: named, ACTIVE, verified, carrying its exact pool contract.
        GameEntity game = games.findById(gameId).orElseThrow();
        assertThat(game.getName()).isEqualTo("Test Campaign");
        assertThat(game.getStatus()).isEqualTo(GameStatus.ACTIVE);
        assertThat(game.isVerificationPassed()).isTrue();
        assertThat(game.getPoolContract()).isNotNull();
        // designedRtp == Σ(tierValue×count) / (price×total): 168/600 = 0.28.
        assertThat(game.getPoolContract().toDomain().payoutRatio())
                .isEqualByComparingTo(new BigDecimal("168").divide(new BigDecimal("600"), 10, RoundingMode.HALF_UP));

        // Books exist only in the chosen shops; every allocated book points at one of them.
        List<TicketBookEntity> gameBooks = books.findByGameId(gameId);
        assertThat(gameBooks).isNotEmpty();
        assertThat(gameBooks).allSatisfy(b -> {
            if (b.getDealerId() != null) {
                assertThat(b.getDealerId()).isIn(shopLower, shopMiddle, shopUpper);
            }
        });

        // stockedGames updated on each chosen shop.
        assertThat(dealers.findById(shopLower).orElseThrow().getStockedGames()).contains(gameId);
        assertThat(dealers.findById(shopMiddle).orElseThrow().getStockedGames()).contains(gameId);
        assertThat(dealers.findById(shopUpper).orElseThrow().getStockedGames()).contains(gameId);

        // Analytics reports the same server-derived designed RTP at scale 6.
        mockMvc.perform(get("/api/master/campaigns/" + gameId + "/analytics").with(master()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designedRtp").value(0.28))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalTickets").value(120));
    }

    @Test
    void invalidTiersAreRejectedWith422() throws Exception {
        // 200 winners of a 10-coin prize over only 120 tickets overspends the revenue — the pool
        // validator rejects it (payout ratio out of range / negative loser count) as a 422.
        String body = """
                {
                  "name": "Bad Campaign",
                  "mechanicType": "DEMON_SEAL",
                  "price": 5,
                  "tiers": [ {"value": 10, "count": 200, "label": "Too many"} ],
                  "totalTickets": 120,
                  "books": 6,
                  "nearMissMode": "CLEAN",
                  "bookVisibility": "NONE",
                  "shopIds": ["%s", "%s", "%s"]
                }
                """.formatted(shopLower, shopMiddle, shopUpper);
        mockMvc.perform(post("/api/master/campaigns")
                        .with(master()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_POOL"));
    }

    @Test
    void emptyBodyIs400ValidationError() throws Exception {
        mockMvc.perform(post("/api/master/campaigns")
                        .with(master()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- preview -------------------------------------------------------------

    @Test
    void previewIsServerDerivedAndPersistsNothing() throws Exception {
        long before = games.count();
        mockMvc.perform(post("/api/master/campaigns/preview")
                        .with(master()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designedRtp").value(0.28))
                .andExpect(jsonPath("$.winnerCount").value(36))
                .andExpect(jsonPath("$.prizeBudget").value(168.0))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors").isEmpty());
        // Preview created no game.
        assertThat(games.count()).isEqualTo(before);
    }

    @Test
    void previewCollectsValidationErrorsWithoutThrowing() throws Exception {
        String body = """
                {
                  "name": "Preview Bad",
                  "mechanicType": "DEMON_SEAL",
                  "price": 5,
                  "tiers": [ {"value": 10, "count": 200, "label": "Too many"} ],
                  "totalTickets": 120,
                  "books": 6,
                  "nearMissMode": "CLEAN",
                  "bookVisibility": "NONE",
                  "shopIds": ["%s"]
                }
                """.formatted(shopLower);
        mockMvc.perform(post("/api/master/campaigns/preview")
                        .with(master()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    // --- analytics reconciliation --------------------------------------------

    @Test
    void analyticsReconcilesWithTheLedgerAfterBuyAndReveal() throws Exception {
        UUID gameId = createCampaign();
        UUID bookId = allocatedBook(gameId);
        UUID player = fundedPlayer();

        // Two ordinary purchases: 2 × 5 = 10 in SPEND.
        buy(bookId, player);
        buy(bookId, player);

        // Force a WIN onto the ledger: hand the player an unsold winner from an allocated book, reveal it.
        TicketEntity winner = tickets.findByGameId(gameId).stream()
                .filter(t -> t.getStatus() == TicketStatus.AVAILABLE)
                .filter(t -> t.getPrizeAmount().signum() > 0)
                .filter(t -> t.getBookId() != null
                        && books.findById(t.getBookId()).map(b -> b.getDealerId() != null).orElse(false))
                .findFirst().orElseThrow();
        winner.setStatus(TicketStatus.SOLD);
        winner.setPlayerId(player);
        tickets.save(winner);
        BigDecimal winnerPrize = winner.getPrizeAmount();
        reveal(winner.getId(), player);

        String analytics = mockMvc.perform(
                        get("/api/master/campaigns/" + gameId + "/analytics").with(master()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sold").value(3))
                .andReturn().getResponse().getContentAsString();

        // grossSales == Σ SPEND (2 × 5), paidOut == Σ WIN (the winner's prize).
        assertThat(new BigDecimal(JsonPath.read(analytics, "$.grossSales").toString()))
                .isEqualByComparingTo("10");
        assertThat(new BigDecimal(JsonPath.read(analytics, "$.paidOut").toString()))
                .isEqualByComparingTo(winnerPrize);
        assertThat(new BigDecimal(JsonPath.read(analytics, "$.houseNet").toString()))
                .isEqualByComparingTo(new BigDecimal("10").subtract(winnerPrize));
        // realizedRtp == paidOut / grossSales, scale 6.
        assertThat(new BigDecimal(JsonPath.read(analytics, "$.realizedRtp").toString()))
                .isEqualByComparingTo(winnerPrize.divide(new BigDecimal("10"), 6, RoundingMode.HALF_UP));
        // The per-shop breakdown is populated.
        assertThat(((Number) JsonPath.read(analytics, "$.shops.length()")).intValue()).isGreaterThan(0);
    }

    // --- lifecycle -----------------------------------------------------------

    @Test
    void retireBlocksPurchaseButRevealStillWorksAndActivateRestores() throws Exception {
        UUID gameId = createCampaign();
        UUID bookId = allocatedBook(gameId);
        UUID player = fundedPlayer();

        // Buy a ticket while the campaign is ACTIVE.
        UUID boughtTicket = buy(bookId, player);

        // Retire the campaign.
        mockMvc.perform(post("/api/master/campaigns/" + gameId + "/retire").with(master()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"));

        // Purchase from a retired campaign is refused (409) ...
        mockMvc.perform(post("/api/books/" + bookId + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + player + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // ... but revealing an already-bought ticket still works (200).
        mockMvc.perform(post("/api/tickets/" + boughtTicket + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + player + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true));

        // Reactivating restores purchasing.
        mockMvc.perform(post("/api/master/campaigns/" + gameId + "/activate").with(master()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/books/" + bookId + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + player + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketStatus").value("SOLD"));
    }

    // --- restock -------------------------------------------------------------

    @Test
    void restockUsesThePersistedContractAndRetiredRestockIs409() throws Exception {
        UUID gameId = createCampaign();
        long booksBefore = books.findByGameId(gameId).size();
        BigDecimal rtpBefore = games.findById(gameId).orElseThrow().getPoolContract().toDomain().payoutRatio();

        // Restock regenerates from the persisted contract: book count grows, economics unchanged.
        mockMvc.perform(post("/api/master/games/" + gameId + "/restock").with(master()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsAdded").value(120));

        assertThat((long) books.findByGameId(gameId).size()).isGreaterThan(booksBefore);
        assertThat(games.findById(gameId).orElseThrow().getPoolContract().toDomain().payoutRatio())
                .isEqualByComparingTo(rtpBefore); // RTP structure identical

        // Retire, then restock is refused (409).
        mockMvc.perform(post("/api/master/campaigns/" + gameId + "/retire").with(master()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/master/games/" + gameId + "/restock").with(master()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // --- security ------------------------------------------------------------

    @Test
    void allCampaignEndpointsRequireMasterLogin() throws Exception {
        UUID gameId = createCampaign();

        mockMvc.perform(get("/api/master/campaigns"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/master/campaigns").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/master/campaigns/preview").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/master/campaigns/" + gameId + "/analytics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/master/campaigns/" + gameId + "/retire").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/master/campaigns/" + gameId + "/activate").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsCampaignsForTheMaster() throws Exception {
        UUID gameId = createCampaign();
        mockMvc.perform(get("/api/master/campaigns").with(master()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gameId").value(gameId.toString()))
                .andExpect(jsonPath("$[0].name").value("Test Campaign"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].mechanic").value("DEMON_SEAL"))
                .andExpect(jsonPath("$[0].total").value(120));
    }

    // --- helpers -------------------------------------------------------------

    private UUID buy(UUID bookId, UUID player) throws Exception {
        String purchased = mockMvc.perform(post("/api/books/" + bookId + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + player + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(purchased, "$.ticketId"));
    }

    private void reveal(UUID ticketId, UUID player) throws Exception {
        mockMvc.perform(post("/api/tickets/" + ticketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + player + "\"}"))
                .andExpect(status().isOk());
    }
}
