package com.luckledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private UUID firstTicketId;

    @BeforeEach
    void seed() {
        gameId = UUID.randomUUID();
        GameSetupResult setup = TestGames.demonGame();
        PersistedGame persisted =
                GamePersistenceMapper.toPersisted(gameId, ApiConfig.demonConfig(), setup, Instant.now());
        games.save(persisted.game());
        dealers.saveAll(persisted.dealers());
        books.saveAll(persisted.books());
        tickets.saveAll(persisted.tickets());

        TicketBookEntity allocated = persisted.books().stream()
                .filter(b -> b.getDealerId() != null)
                .findFirst()
                .orElseThrow();
        bookId = allocated.getId();
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
                .andExpect(jsonPath("$[0].ticketCount").value(20));

        mockMvc.perform(get("/api/games/" + gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanic").value("DEMON_SEAL"))
                .andExpect(jsonPath("$.ticketCount").value(20))
                .andExpect(jsonPath("$.dealerCount").value(3))
                .andExpect(jsonPath("$.verificationPassed").value(true));
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
    void listsAndGetsBookMetadataWithoutLeakingTickets() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalTickets").isNumber())
                .andExpect(jsonPath("$[0].ticketsRemaining").isNumber());

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
        mockMvc.perform(get("/api/tickets/" + firstTicketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.isWinner").doesNotExist());

        String body = "{\"playerId\":\"" + fundedPlayer() + "\"}";
        mockMvc.perform(post("/api/tickets/" + firstTicketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.isWinner").exists());

        mockMvc.perform(post("/api/tickets/" + firstTicketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true));

        mockMvc.perform(get("/api/tickets/" + firstTicketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true));
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
