package com.luckledger.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.api.persistence.GamePersistenceMapper;
import com.luckledger.api.persistence.GamePersistenceMapper.PersistedGame;
import com.luckledger.api.persistence.GameRepository;
import com.luckledger.api.persistence.PlayerEntity;
import com.luckledger.api.persistence.PlayerRepository;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.distribution.DealerTier;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.generation.MetadataVisibility;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.scratch.TicketStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end MockMvc coverage of the REST surface against the real Postgres-backed stores
 * (Testcontainers): games/books/dealers reads, player create/borrow, and the purchase→reveal flow
 * through the transactional gateways. Replaces the former standalone (in-memory) controller tests now
 * that the stores are DB-backed. {@code @Transactional} rolls back each test, so a fresh Demon Seal
 * game is seeded per test.
 */
@SpringBootTest(classes = {
        TestApplication.class,
        ApiConfig.class,
        GameStore.class,
        PlayerRegistry.class,
        PurchaseGateway.class,
        RevealGateway.class,
        RevealNarrator.class,
        GameController.class,
        BookController.class,
        DealerController.class,
        TicketController.class,
        PlayerController.class,
        LedgerController.class,
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
class ApiEndpointsIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameRepository games;

    @Autowired
    private DealerRepository dealers;

    @Autowired
    private TicketBookRepository books;

    @Autowired
    private TicketRepository tickets;

    @Autowired
    private PlayerRepository players;

    private UUID gameId;
    private UUID bookId;
    private UUID dealerId;
    private UUID firstTicketId;

    @BeforeEach
    void seed() {
        gameId = UUID.randomUUID();
        GameSetupResult setup = TestGames.demonGame();
        PersistedGame persisted =
                GamePersistenceMapper.toPersisted(gameId, ApiConfig.demonConfig(), setup, Instant.now());
        games.save(persisted.game());
        // One shop per allocation slot (shop id == the slot's dealer id), stocking this game.
        setup.dealers().forEach(d -> dealers.save(new DealerEntity(
                d.dealerId(), d.name(), "Owner", null, List.of(gameId),
                d.tier(), d.rankScore(), d.booksPerCycle(), d.booksDepleted())));
        books.saveAll(persisted.books());
        tickets.saveAll(persisted.tickets());

        TicketBookEntity allocated = persisted.books().stream()
                .filter(b -> b.getDealerId() != null)
                .findFirst()
                .orElseThrow();
        bookId = allocated.getId();
        dealerId = allocated.getDealerId();
        firstTicketId = persisted.tickets().stream()
                .filter(t -> bookId.equals(t.getBookId()) && Integer.valueOf(0).equals(t.getPositionInBook()))
                .map(TicketEntity::getId)
                .findFirst()
                .orElseThrow();
    }

    private UUID fundedPlayer() {
        UUID id = UUID.randomUUID();
        players.save(new PlayerEntity(
                id, "Player", new BigDecimal("100"), new BigDecimal("100"),
                BigDecimal.ZERO, BigDecimal.ZERO, 0, Instant.now()));
        return id;
    }

    // --- games ---------------------------------------------------------------

    @Test
    void listsAndSummarizesTheSeededGame() throws Exception {
        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mechanic").value("DEMON_SEAL"))
                .andExpect(jsonPath("$[0].gameName").value("Demon Seal"))
                .andExpect(jsonPath("$[0].ticketCount").value(500))
                .andExpect(jsonPath("$[0].ticketPrice").value(5))
                .andExpect(jsonPath("$[0].topPrize").value(300));

        mockMvc.perform(get("/api/games/" + gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanic").value("DEMON_SEAL"))
                .andExpect(jsonPath("$.ticketCount").value(500))
                .andExpect(jsonPath("$.dealerCount").value(5))
                .andExpect(jsonPath("$.verificationPassed").value(true));
    }

    @Test
    void houseOverviewExposesPoolEconomics() throws Exception {
        // Before any sale the pool's economics are already fixed: the prize fund is known in full.
        mockMvc.perform(get("/api/house/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.ticketsSold").value(0))
                .andExpect(jsonPath("$.totals.revenue").value(0))
                .andExpect(jsonPath("$.games[0].gameName").value("Demon Seal"))
                .andExpect(jsonPath("$.games[0].totalTickets").value(500))
                .andExpect(jsonPath("$.games[0].prizeFund").value(1620.0))
                .andExpect(jsonPath("$.games[0].maxRevenue").value(2500))
                .andExpect(jsonPath("$.games[0].topPrize").value(300.0))
                .andExpect(jsonPath("$.games[0].books.total").value(20));

        // A sale and reveal move the running totals.
        UUID playerId = fundedPlayer();
        String body = "{\"playerId\":\"" + playerId + "\"}";
        String purchased = mockMvc.perform(post("/api/books/" + bookId + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticketId = com.jayway.jsonpath.JsonPath.read(purchased, "$.ticketId");
        mockMvc.perform(post("/api/tickets/" + ticketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/house/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.ticketsSold").value(1))
                .andExpect(jsonPath("$.totals.ticketsRevealed").value(1))
                .andExpect(jsonPath("$.totals.revenue").value(5));
    }

    // --- auth & master --------------------------------------------------------

    @Test
    void houseIsPublicButMasterToolsRequireLogin() throws Exception {
        // The house overview is deliberately public — anyone can read the pool economics.
        mockMvc.perform(get("/api/house/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.ticketsSold").value(0))
                .andExpect(jsonPath("$.games[0].gameName").value("Demon Seal"));
        // The master tools stay gated: anonymous access is refused.
        mockMvc.perform(get("/api/master/players"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        // Player-facing surface stays public.
        mockMvc.perform(get("/api/dealers")).andExpect(status().isOk());
    }

    @Test
    void masterCanLogInAndOut() throws Exception {
        mockMvc.perform(formLogin("/api/auth/login").user("master").password("test-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("master"));

        mockMvc.perform(formLogin("/api/auth/login").user("master").password("wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
        mockMvc.perform(get("/api/auth/me").with(user("master").roles("MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("master"));
    }

    @Test
    void masterSeesPlayersAndGrantsCoins() throws Exception {
        UUID playerId = fundedPlayer();
        mockMvc.perform(get("/api/master/players").with(user("master").roles("MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(playerId.toString()))
                .andExpect(jsonPath("$[0].coinBalance").value(100))
                .andExpect(jsonPath("$[0].pendingTickets").value(0));

        // A grant lands as an ordinary bank loan: balance up, recorded against totalBorrowed.
        mockMvc.perform(post("/api/master/players/" + playerId + "/grant")
                        .with(user("master").roles("MASTER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coinBalance").value(150))
                .andExpect(jsonPath("$.totalBorrowed").value(150));
    }

    @Test
    void masterRestocksAGameThroughTheVerifiedPipeline() throws Exception {
        mockMvc.perform(post("/api/master/games/" + gameId + "/restock")
                        .with(user("master").roles("MASTER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booksAdded").value(20))
                .andExpect(jsonPath("$.ticketsAdded").value(500));

        // The game's pool doubled and the new books are allocated to the stocking shops.
        mockMvc.perform(get("/api/house/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[0].totalTickets").value(1000))
                .andExpect(jsonPath("$.games[0].books.total").value(40));
    }

    @Test
    void exposesVerificationAndNearMissReports() throws Exception {
        mockMvc.perform(get("/api/games/" + gameId + "/verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.checks").isNotEmpty());

        mockMvc.perform(get("/api/games/" + gameId + "/near-misses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLosers").isNumber());
    }

    @Test
    void unknownGameIs404() throws Exception {
        mockMvc.perform(get("/api/games/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- books & dealers -----------------------------------------------------

    @Test
    void listsAShopsBooksAndGetsOneWithoutLeakingTickets() throws Exception {
        // Books are reachable only through their shop — there is no flat /api/books catalogue.
        mockMvc.perform(get("/api/dealers/" + dealerId + "/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalTickets").isNumber())
                .andExpect(jsonPath("$[0].ticketsRemaining").isNumber())
                .andExpect(jsonPath("$[0].gameName").value("Demon Seal"))
                .andExpect(jsonPath("$[0].mechanic").value("DEMON_SEAL"))
                .andExpect(jsonPath("$[0].ticketPrice").value(5)); // price shown before buying

        mockMvc.perform(get("/api/books/" + bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookId.toString()))
                .andExpect(jsonPath("$.tickets").doesNotExist())
                .andExpect(jsonPath("$.bookValue").doesNotExist());

        mockMvc.perform(get("/api/books/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // --- book metadata visibility (§3.12) ------------------------------------

    @Test
    void bookVisibilityIsEnforcedServerSidePerTier() throws Exception {
        // Reuse the seeded game's allocated shop; attach one 3-ticket book per visibility tier to it,
        // with a known winner (10) at position 0 so a FULL book's numbers can be reconciled after a reveal.
        UUID none = tierBook(MetadataVisibility.NONE);
        UUID partial = tierBook(MetadataVisibility.PARTIAL);
        UUID full = tierBook(MetadataVisibility.FULL);

        // NONE: counts only — every data field is withheld server-side.
        mockMvc.perform(get("/api/books/" + none))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("NONE"))
                .andExpect(jsonPath("$.totalTickets").value(3))
                .andExpect(jsonPath("$.percentDispensed").doesNotExist())
                .andExpect(jsonPath("$.prizesDispensed").doesNotExist())
                .andExpect(jsonPath("$.estimatedRemainingValue").doesNotExist())
                .andExpect(jsonPath("$.winFrequencySoFar").doesNotExist());

        // PARTIAL: percentDispensed only; the value fields stay withheld.
        mockMvc.perform(get("/api/books/" + partial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PARTIAL"))
                .andExpect(jsonPath("$.percentDispensed").exists())
                .andExpect(jsonPath("$.prizesDispensed").doesNotExist())
                .andExpect(jsonPath("$.estimatedRemainingValue").doesNotExist())
                .andExpect(jsonPath("$.winFrequencySoFar").doesNotExist());

        // FULL: everything present, and before any reveal nothing has been dispensed yet — but the whole
        // 10-coin prize fund is already known (it was fixed at print time).
        String fresh = mockMvc.perform(get("/api/books/" + full))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("FULL"))
                .andExpect(jsonPath("$.percentDispensed").exists())
                .andExpect(jsonPath("$.prizesDispensed").exists())
                .andExpect(jsonPath("$.estimatedRemainingValue").exists())
                .andExpect(jsonPath("$.winFrequencySoFar").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(JsonPath.read(fresh, "$.prizesDispensed").toString()))
                .isEqualByComparingTo("0");
        assertThat(new BigDecimal(JsonPath.read(fresh, "$.estimatedRemainingValue").toString()))
                .isEqualByComparingTo("10");

        // Buy and scratch the FULL book's position-0 ticket (the known 10-coin winner), through the
        // ownership-gated reveal, then confirm the FULL numbers reconcile against what was revealed.
        UUID playerId = fundedPlayer();
        String body = "{\"playerId\":\"" + playerId + "\"}";
        String purchased = mockMvc.perform(post("/api/books/" + full + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticketId = JsonPath.read(purchased, "$.ticketId");
        mockMvc.perform(post("/api/tickets/" + ticketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isWinner").value(true));

        TicketEntity revealed = tickets.findById(UUID.fromString(ticketId)).orElseThrow();
        String reconciled = mockMvc.perform(get("/api/books/" + full))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(JsonPath.read(reconciled, "$.prizesDispensed").toString()))
                .isEqualByComparingTo(revealed.getRevealedPrize());
        assertThat(new BigDecimal(JsonPath.read(reconciled, "$.estimatedRemainingValue").toString()))
                .isEqualByComparingTo("0"); // the 10-coin winner was the whole fund
        assertThat(((Number) JsonPath.read(reconciled, "$.winFrequencySoFar")).longValue()).isEqualTo(1L);
        // One of three tickets sold => 33.33% dispensed.
        assertThat(new BigDecimal(JsonPath.read(reconciled, "$.percentDispensed").toString()))
                .isGreaterThan(new BigDecimal("33")).isLessThan(new BigDecimal("34"));
    }

    /**
     * Attaches a fresh 3-ticket book (positions 0–2, prizes 10/0/0) to the seeded game's allocated shop,
     * stamped with the given visibility. Reuses an existing ticket's JSONB grids so the rows are valid.
     */
    private UUID tierBook(MetadataVisibility visibility) {
        TicketBookEntity source = books.findById(bookId).orElseThrow();
        TicketEntity template = tickets.findByBookIdOrderByPositionInBookAsc(bookId).get(0);
        UUID newBookId = UUID.randomUUID();
        books.save(new TicketBookEntity(
                newBookId, gameId, dealerId, source.getPoolContractId(), 3, 0, visibility));
        BigDecimal[] prizes = {new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO};
        for (int position = 0; position < prizes.length; position++) {
            tickets.save(new TicketEntity(
                    UUID.randomUUID(), newBookId, gameId, UUID.randomUUID(), MechanicType.DEMON_SEAL,
                    prizes[position], position, TicketStatus.AVAILABLE,
                    template.getGrid(), template.getSkinnedGrid()));
        }
        return newBookId;
    }

    // --- shop rankings (§2.3) -------------------------------------------------

    @Test
    void rankingsAreSortedByBooksDepletedThenShopName() throws Exception {
        dealers.save(rankShop("Top Shop", 999, DealerTier.TIER_3));
        dealers.save(rankShop("AAA Shop", 5, DealerTier.TIER_1));
        dealers.save(rankShop("ZZZ Shop", 5, DealerTier.TIER_1));

        String json = mockMvc.perform(get("/api/dealers/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].shopName").value("Top Shop"))
                .andExpect(jsonPath("$[0].booksDepleted").value(999))
                .andExpect(jsonPath("$[0].tier").value("TIER_3"))
                .andExpect(jsonPath("$[0].quartile").value("UPPER"))
                .andReturn().getResponse().getContentAsString();

        List<Integer> depleted = JsonPath.read(json, "$[*].booksDepleted");
        for (int i = 1; i < depleted.size(); i++) {
            assertThat(depleted.get(i)).isLessThanOrEqualTo(depleted.get(i - 1));
        }
        List<Integer> ranks = JsonPath.read(json, "$[*].rank");
        for (int i = 0; i < ranks.size(); i++) {
            assertThat(ranks.get(i)).isEqualTo(i + 1);
        }
        List<String> names = JsonPath.read(json, "$[*].shopName");
        assertThat(names.indexOf("AAA Shop")).isLessThan(names.indexOf("ZZZ Shop"));
    }

    @Test
    void rankingsRouteIsNotSwallowedByTheDealerIdPathVariable() throws Exception {
        // "rankings" must reach the rankings handler, not be parsed as a {dealerId} UUID (which would
        // 400 via the MethodArgumentTypeMismatch handler).
        mockMvc.perform(get("/api/dealers/rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        // A genuinely malformed id still 400s through the type-mismatch handler.
        mockMvc.perform(get("/api/dealers/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private DealerEntity rankShop(String shopName, int booksDepleted, DealerTier tier) {
        return new DealerEntity(
                UUID.randomUUID(), shopName, "Owner", null, List.of(), tier, 0, 50, booksDepleted);
    }

    @Test
    void listsAndGetsDealers() throws Exception {
        mockMvc.perform(get("/api/dealers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shopName").isNotEmpty())
                .andExpect(jsonPath("$[0].ownerName").isNotEmpty())
                .andExpect(jsonPath("$[0].games").isArray())
                .andExpect(jsonPath("$[0].games[0].gameName").value("Demon Seal"))
                .andExpect(jsonPath("$[0].tier").isNotEmpty())
                .andExpect(jsonPath("$[0].quartile").isNotEmpty());

        mockMvc.perform(get("/api/dealers/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // --- players -------------------------------------------------------------

    @Test
    void createsGetsAndBorrows() throws Exception {
        String created = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coinBalance").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID playerId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.playerId"));

        mockMvc.perform(get("/api/players/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice"));

        mockMvc.perform(post("/api/players/" + playerId + "/borrow")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coinBalance").value(100))
                .andExpect(jsonPath("$.totalBorrowed").value(100));
    }

    @Test
    void borrowingANonPositiveAmountIsRejectedAtTheBoundary() throws Exception {
        UUID playerId = fundedPlayer();
        // Bean validation (@Positive) rejects a zero amount before the controller runs: 400.
        mockMvc.perform(post("/api/players/" + playerId + "/borrow")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // A negative amount is likewise rejected at the boundary.
        mockMvc.perform(post("/api/players/" + playerId + "/borrow")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // And an absurdly large amount trips @DecimalMax.
        mockMvc.perform(post("/api/players/" + playerId + "/borrow")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void creatingAPlayerWithoutADisplayNameIs400() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownPlayerIs404() throws Exception {
        mockMvc.perform(get("/api/players/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- purchase & reveal ---------------------------------------------------

    @Test
    void purchaseDebitsAndMarksSold() throws Exception {
        mockMvc.perform(post("/api/books/" + bookId + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + fundedPlayer() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketStatus").value("SOLD"))
                .andExpect(jsonPath("$.coinsDeducted").value(5));
    }

    @Test
    void purchaseWithoutFundsIs402() throws Exception {
        UUID broke = UUID.randomUUID();
        players.save(new PlayerEntity(
                broke, "Broke", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, Instant.now()));
        mockMvc.perform(post("/api/books/" + bookId + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"playerId\":\"" + broke + "\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    void ticketIsMaskedBeforeRevealThenRevealedIdempotently() throws Exception {
        // Masked: no outcome AND no grid — the client cannot evaluate the ticket early.
        mockMvc.perform(get("/api/tickets/" + firstTicketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.isWinner").doesNotExist())
                .andExpect(jsonPath("$.grid").doesNotExist());

        // Reveal is ownership-gated, so the ticket must be bought first — the buyer then scratches it.
        UUID playerId = fundedPlayer();
        String body = "{\"playerId\":\"" + playerId + "\"}";
        String purchased = mockMvc.perform(post("/api/books/" + bookId + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticketId = com.jayway.jsonpath.JsonPath.read(purchased, "$.ticketId");

        mockMvc.perform(post("/api/tickets/" + ticketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.isWinner").exists())
                // Revealed: the real themed grid is served (a 3x3 Demon Seal grid = 9 cells).
                .andExpect(jsonPath("$.gameId").value(gameId.toString()))
                .andExpect(jsonPath("$.grid.dimension").value(3))
                .andExpect(jsonPath("$.grid.cells.length()").value(9))
                .andExpect(jsonPath("$.grid.cells[0].abstractSymbol").isNotEmpty());

        mockMvc.perform(post("/api/tickets/" + ticketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.grid.cells.length()").value(9));

        mockMvc.perform(get("/api/tickets/" + ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.grid.cells.length()").value(9));
    }

    @Test
    void revealCarriesABackendServedNarrativeConsistentWithThePrize() throws Exception {
        // Pre-reveal: the masked view carries NO narrative (the outcome is not decided for the client yet).
        mockMvc.perform(get("/api/tickets/" + firstTicketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.narrative").doesNotExist());

        UUID playerId = fundedPlayer();
        String body = "{\"playerId\":\"" + playerId + "\"}";
        String purchased = mockMvc.perform(post("/api/books/" + bookId + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticketId = JsonPath.read(purchased, "$.ticketId");

        // Revealed (seeded game is Demon Seal): the narrative is present, in the education voice, and its
        // seal score is consistent with the prize (a winner scores at least the 4-point floor).
        String revealed = mockMvc.perform(post("/api/tickets/" + ticketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrative").exists())
                .andExpect(jsonPath("$.narrative.summary").isNotEmpty())
                .andExpect(jsonPath("$.narrative.sealScore").isNumber())
                .andExpect(jsonPath("$.narrative.pointsNeeded").value(4))
                .andExpect(jsonPath("$.narrative.matchedPositions").isArray())
                .andReturn().getResponse().getContentAsString();

        boolean winner = JsonPath.read(revealed, "$.isWinner");
        int sealScore = JsonPath.read(revealed, "$.narrative.sealScore");
        BigDecimal prize = new BigDecimal(JsonPath.read(revealed, "$.prizeAmount").toString());
        if (winner) {
            assertThat(sealScore).isGreaterThanOrEqualTo(4);
            assertThat(prize).isGreaterThan(BigDecimal.ZERO);
            assertThat(((Boolean) JsonPath.read(revealed, "$.narrative.nearMiss"))).isFalse();
        } else {
            assertThat(sealScore).isLessThan(4);
            assertThat(prize).isEqualByComparingTo(BigDecimal.ZERO);
        }

        // The re-read revealed view carries the same narrative shape (idempotent, still explained).
        mockMvc.perform(get("/api/tickets/" + ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrative.summary").isNotEmpty())
                .andExpect(jsonPath("$.narrative.sealScore").isNumber());
    }

    @Test
    void revealingAnUnsoldTicketIs409() throws Exception {
        // firstTicketId was never bought, so it has no owner: revealing it is a conflict.
        mockMvc.perform(post("/api/tickets/" + firstTicketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + fundedPlayer() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void revealingSomeoneElsesTicketIs403() throws Exception {
        UUID buyer = fundedPlayer();
        UUID stranger = fundedPlayer();
        String purchased = mockMvc.perform(post("/api/books/" + bookId + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + buyer + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticketId = com.jayway.jsonpath.JsonPath.read(purchased, "$.ticketId");

        // A different player may not reveal (and be credited for) a ticket they did not buy.
        mockMvc.perform(post("/api/tickets/" + ticketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + stranger + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TICKET_OWNER"));
    }

    @Test
    void winningRevealAttributesTheWinToTheStockingShop() throws Exception {
        // Pick a guaranteed-winning ticket from an allocated book and hand it to a player (bypassing
        // the sequential purchase so we can land on a specific winner), then scratch it.
        TicketEntity winner = tickets.findAll().stream()
                .filter(t -> t.getBookId() != null)
                .filter(t -> t.getPrizeAmount().signum() > 0)
                .filter(t -> books.findById(t.getBookId())
                        .map(b -> b.getDealerId() != null).orElse(false))
                .findFirst()
                .orElseThrow();
        UUID shop = books.findById(winner.getBookId()).orElseThrow().getDealerId();

        UUID playerId = fundedPlayer();
        winner.setStatus(com.luckledger.domain.scratch.TicketStatus.SOLD);
        winner.setPlayerId(playerId);
        tickets.save(winner);

        mockMvc.perform(post("/api/tickets/" + winner.getId() + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + playerId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isWinner").value(true));

        // The WIN carried the book's shop id, so the player's per-shop comparison shows a real payout.
        String comparison = mockMvc.perform(get("/api/ledger/" + playerId + "/dealer-comparison"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number wonAtShop = com.jayway.jsonpath.JsonPath.read(comparison, "$['" + shop + "'].totalWon");
        org.assertj.core.api.Assertions.assertThat(wonAtShop.doubleValue()).isGreaterThan(0.0);
    }

    @Test
    void pendingTicketsListsBoughtButUnscratchedTickets() throws Exception {
        UUID playerId = fundedPlayer();
        String body = "{\"playerId\":\"" + playerId + "\"}";

        mockMvc.perform(get("/api/players/" + playerId + "/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        String purchased = mockMvc.perform(post("/api/books/" + bookId + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticketId = com.jayway.jsonpath.JsonPath.read(purchased, "$.ticketId");

        // The bought ticket is recoverable (e.g. after a refresh) until it is scratched...
        mockMvc.perform(get("/api/players/" + playerId + "/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticketId").value(ticketId))
                .andExpect(jsonPath("$[0].mechanic").value("DEMON_SEAL"))
                .andExpect(jsonPath("$[0].gameName").value("Demon Seal"));

        // ...and drops off the list once revealed.
        mockMvc.perform(post("/api/tickets/" + ticketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/players/" + playerId + "/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/players/" + UUID.randomUUID() + "/tickets"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownTicketAndUnknownBookAre404() throws Exception {
        mockMvc.perform(get("/api/tickets/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(post("/api/books/" + UUID.randomUUID() + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + fundedPlayer() + "\"}"))
                .andExpect(status().isNotFound());
    }
}
