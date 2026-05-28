package com.luckledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GameControllerTest {

    private MockMvc mockMvc;
    private UUID gameId;

    @BeforeEach
    void setUp() {
        GameStore store = new GameStore();
        gameId = store.register(TestGames.demonGame());
        mockMvc = MockMvcBuilders.standaloneSetup(new GameController(store))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsTheSeededGame() throws Exception {
        mockMvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mechanic").value("DEMON_SEAL"))
                .andExpect(jsonPath("$[0].ticketCount").value(20));
    }

    @Test
    void getsAGameSummary() throws Exception {
        mockMvc.perform(get("/api/games/" + gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanic").value("DEMON_SEAL"))
                .andExpect(jsonPath("$.ticketCount").value(20))
                .andExpect(jsonPath("$.dealerCount").value(3))
                .andExpect(jsonPath("$.verificationPassed").value(true));
    }

    @Test
    void exposesTheVerificationReport() throws Exception {
        mockMvc.perform(get("/api/games/" + gameId + "/verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.checks").isNotEmpty());
    }

    @Test
    void exposesTheNearMissReport() throws Exception {
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
}
