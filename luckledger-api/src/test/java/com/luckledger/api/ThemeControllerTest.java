package com.luckledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luckledger.generation.theme.ThemeSkinningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ThemeControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ThemeSkinningService themes = new ApiConfig().themeSkinningService();
        mockMvc = MockMvcBuilders.standaloneSetup(new ThemeController(themes))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsTheSeededThemes() throws Exception {
        mockMvc.perform(get("/api/themes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.themeId=='celestial')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.themeId=='demon')]").isNotEmpty());
    }

    @Test
    void getsAThemeWithItsPalette() throws Exception {
        mockMvc.perform(get("/api/themes/demon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Demon Seal"))
                .andExpect(jsonPath("$.palette.primary").isNotEmpty())
                .andExpect(jsonPath("$.symbolCount").isNumber());
    }

    @Test
    void unknownThemeIs404() throws Exception {
        mockMvc.perform(get("/api/themes/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
