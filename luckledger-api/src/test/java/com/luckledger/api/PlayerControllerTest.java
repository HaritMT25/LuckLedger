package com.luckledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luckledger.domain.player.Player;
import com.luckledger.player.bank.BankService;
import com.luckledger.player.ledger.TransactionRecorder;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlayerControllerTest {

    private PlayerRegistry registry;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry = new PlayerRegistry();
        BankService bank = new BankService(new TransactionRecorder());
        mockMvc = MockMvcBuilders.standaloneSetup(new PlayerController(registry, bank))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsAPlayerWith201AndZeroBalance() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").isNotEmpty())
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.coinBalance").value(0));
    }

    @Test
    void getsAnExistingPlayer() throws Exception {
        Player player = registry.create("Bob");

        mockMvc.perform(get("/api/players/" + player.getPlayerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Bob"));
    }

    @Test
    void borrowingAddsFreeCoins() throws Exception {
        Player player = registry.create("Carol");

        mockMvc.perform(post("/api/players/" + player.getPlayerId() + "/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coinBalance").value(100))
                .andExpect(jsonPath("$.totalBorrowed").value(100));
    }

    @Test
    void borrowingANonPositiveAmountIs422() throws Exception {
        Player player = registry.create("Dave");

        mockMvc.perform(post("/api/players/" + player.getPlayerId() + "/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownPlayerIs404() throws Exception {
        mockMvc.perform(get("/api/players/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
