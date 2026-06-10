package com.luckledger.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.luckledger.distribution.GameSetupResult;
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
        mockMvc.perform(get("/api/house/overview").with(user("master").roles("MASTER")))
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

        mockMvc.perform(get("/api/house/overview").with(user("master").roles("MASTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.ticketsSold").value(1))
                .andExpect(jsonPath("$.totals.ticketsRevealed").value(1))
                .andExpect(jsonPath("$.totals.revenue").value(5));
    }

    // --- auth & master --------------------------------------------------------

    @Test
    void operatorSurfaceRequiresMasterLogin() throws Exception {
        mockMvc.perform(get("/api/house/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/master/players"))
                .andExpect(status().isUnauthorized());
        // Player-facing surface stays public.
        mockMvc.perform(get("/api/dealers")).andExpect(status().isOk());
    }

    @Test
    void masterCanLogInAndOut() throws Exception {
        mockMvc.perform(formLogin("/api/auth/login").user("master").password("scratch-the-truth"))
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
        mockMvc.perform(get("/api/house/overview").with(user("master").roles("MASTER")))
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
    void borrowingANonPositiveAmountIs422() throws Exception {
        UUID playerId = fundedPlayer();
        mockMvc.perform(post("/api/players/" + playerId + "/borrow")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isUnprocessableEntity())
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

        String body = "{\"playerId\":\"" + fundedPlayer() + "\"}";
        mockMvc.perform(post("/api/tickets/" + firstTicketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.isWinner").exists())
                // Revealed: the real themed grid is served (a 3x3 Demon Seal grid = 9 cells).
                .andExpect(jsonPath("$.gameId").value(gameId.toString()))
                .andExpect(jsonPath("$.grid.dimension").value(3))
                .andExpect(jsonPath("$.grid.cells.length()").value(9))
                .andExpect(jsonPath("$.grid.cells[0].abstractSymbol").isNotEmpty());

        mockMvc.perform(post("/api/tickets/" + firstTicketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.grid.cells.length()").value(9));

        mockMvc.perform(get("/api/tickets/" + firstTicketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.grid.cells.length()").value(9));
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
