package com.luckledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MechanicControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MechanicController()).build();
    }

    @Test
    void listsImplementedMechanics() throws Exception {
        mockMvc.perform(get("/api/mechanics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='CELESTIAL_FORTUNE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.type=='DEMON_SEAL')]").isNotEmpty())
                .andExpect(jsonPath("$[0].symbolPoolSize").isNumber());
    }
}
