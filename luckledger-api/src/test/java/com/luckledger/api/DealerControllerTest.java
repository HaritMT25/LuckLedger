package com.luckledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luckledger.distribution.Dealer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DealerControllerTest {

    private MockMvc mockMvc;
    private Dealer aDealer;

    @BeforeEach
    void setUp() {
        GameStore store = new GameStore();
        store.register(ApiConfig.demonConfig(), TestGames.demonGame());
        aDealer = store.dealers().iterator().next();
        mockMvc = MockMvcBuilders.standaloneSetup(new DealerController(store))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsDealers() throws Exception {
        mockMvc.perform(get("/api/dealers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tier").isNotEmpty())
                .andExpect(jsonPath("$[0].quartile").isNotEmpty());
    }

    @Test
    void getsADealer() throws Exception {
        mockMvc.perform(get("/api/dealers/" + aDealer.dealerId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(aDealer.name()))
                .andExpect(jsonPath("$.tier").value("TIER_1"));
    }

    @Test
    void unknownDealerIs404() throws Exception {
        mockMvc.perform(get("/api/dealers/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
