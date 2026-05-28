package com.luckledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.player.Player;
import com.luckledger.player.ledger.TransactionRecorder;
import com.luckledger.mechanic.CelestialFortuneMechanic;
import com.luckledger.mechanic.DemonSealMechanic;
import com.luckledger.scratchflow.ScratchRevealService;
import com.luckledger.scratchflow.TicketPurchaseService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TicketControllerTest {

    private MockMvc mockMvc;
    private PlayerRegistry players;
    private UUID bookId;
    private UUID ticketId;

    @BeforeEach
    void setUp() {
        GameStore store = new GameStore();
        GameSetupResult game = TestGames.demonGame();
        store.register(ApiConfig.demonConfig(), game);
        bookId = store.books().iterator().next().bookId();
        ticketId = game.generationResult().tickets().get(0).ticketId();

        players = new PlayerRegistry();
        TransactionRecorder recorder = new TransactionRecorder();
        Map<MechanicType, ScratchRevealService> reveals = Map.of(
                MechanicType.CELESTIAL_FORTUNE,
                new ScratchRevealService(new CelestialFortuneMechanic().createEvaluator(), recorder),
                MechanicType.DEMON_SEAL,
                new ScratchRevealService(new DemonSealMechanic().createEvaluator(), recorder));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TicketController(store, players, new TicketPurchaseService(recorder), reveals))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UUID fundedPlayer() {
        Player p = players.create("Player");
        p.recordBorrow(new BigDecimal("100"));
        return p.getPlayerId();
    }

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
    void ticketIsMaskedBeforeReveal() throws Exception {
        mockMvc.perform(get("/api/tickets/" + ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.isWinner").doesNotExist());
    }

    @Test
    void revealShowsTheOutcomeAndIsIdempotent() throws Exception {
        String body = "{\"playerId\":\"" + fundedPlayer() + "\"}";

        mockMvc.perform(post("/api/tickets/" + ticketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.isWinner").exists());

        // a second reveal returns the same outcome (idempotent)
        mockMvc.perform(post("/api/tickets/" + ticketId + "/reveal")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true));

        // and a subsequent GET now shows it revealed
        mockMvc.perform(get("/api/tickets/" + ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true));
    }

    @Test
    void unknownTicketIs404() throws Exception {
        mockMvc.perform(get("/api/tickets/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void purchaseFromUnknownBookIs404() throws Exception {
        mockMvc.perform(post("/api/books/" + UUID.randomUUID() + "/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + fundedPlayer() + "\"}"))
                .andExpect(status().isNotFound());
    }
}
