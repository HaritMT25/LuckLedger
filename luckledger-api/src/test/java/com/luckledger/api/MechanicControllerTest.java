package com.luckledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.luckledger.mechanic.CelestialFortuneEvaluator;
import com.luckledger.mechanic.DemonSealEvaluator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MechanicControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MechanicController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsImplementedMechanics() throws Exception {
        mockMvc.perform(get("/api/mechanics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='CELESTIAL_FORTUNE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.type=='DEMON_SEAL')]").isNotEmpty())
                .andExpect(jsonPath("$[0].symbolPoolSize").isNumber());
    }

    @Test
    void celestialDetail_carriesLadderConsistentWithEvaluatorAndExampleGrids() throws Exception {
        String json = mockMvc.perform(get("/api/mechanics/CELESTIAL_FORTUNE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CELESTIAL_FORTUNE"))
                .andExpect(jsonPath("$.displayName").value("Celestial Fortune"))
                .andExpect(jsonPath("$.gridDimension").value(4))
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.winRules").isArray())
                .andExpect(jsonPath("$.winRules.length()").value(3))
                .andExpect(jsonPath("$.exampleWin.dimension").value(4))
                .andExpect(jsonPath("$.exampleWin.cells.length()").value(16))
                .andExpect(jsonPath("$.exampleLoss.dimension").value(4))
                .andExpect(jsonPath("$.exampleLoss.cells.length()").value(16))
                .andReturn().getResponse().getContentAsString();

        // Every rung's prize must equal what the real evaluator awards for that many matches.
        List<Integer> thresholds = JsonPath.read(json, "$.winRules[*].threshold");
        for (int i = 0; i < thresholds.size(); i++) {
            int threshold = thresholds.get(i);
            BigDecimal prize = new BigDecimal(JsonPath.read(json, "$.winRules[" + i + "].prize").toString());
            assertThat(prize).isEqualByComparingTo(CelestialFortuneEvaluator.prizeForMatches(threshold));
        }
    }

    @Test
    void demonDetail_carriesLadderConsistentWithEvaluator() throws Exception {
        String json = mockMvc.perform(get("/api/mechanics/DEMON_SEAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEMON_SEAL"))
                .andExpect(jsonPath("$.displayName").value("Demon Seal"))
                .andExpect(jsonPath("$.gridDimension").value(3))
                .andExpect(jsonPath("$.winRules").isArray())
                .andExpect(jsonPath("$.winRules.length()").value(9)) // T = 4..12
                .andExpect(jsonPath("$.exampleWin.cells.length()").value(9))
                .andReturn().getResponse().getContentAsString();

        List<Integer> thresholds = JsonPath.read(json, "$.winRules[*].threshold");
        for (int i = 0; i < thresholds.size(); i++) {
            int threshold = thresholds.get(i);
            BigDecimal prize = new BigDecimal(JsonPath.read(json, "$.winRules[" + i + "].prize").toString());
            assertThat(prize).isEqualByComparingTo(DemonSealEvaluator.prizeForPoints(threshold));
        }
    }

    @Test
    void unknownMechanicIs404() throws Exception {
        mockMvc.perform(get("/api/mechanics/NONSENSE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aValidButUnimplementedMechanicIs404() throws Exception {
        // MATCH_3 is a known enum value, but no mechanic is implemented for it yet.
        mockMvc.perform(get("/api/mechanics/MATCH_3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
