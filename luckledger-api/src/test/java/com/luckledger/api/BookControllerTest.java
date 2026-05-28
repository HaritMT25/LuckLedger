package com.luckledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luckledger.distribution.TicketBook;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookControllerTest {

    private MockMvc mockMvc;
    private TicketBook aBook;

    @BeforeEach
    void setUp() {
        GameStore store = new GameStore();
        store.register(ApiConfig.demonConfig(), TestGames.demonGame());
        aBook = store.books().iterator().next();
        mockMvc = MockMvcBuilders.standaloneSetup(new BookController(store))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsBookMetadata() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalTickets").isNumber())
                .andExpect(jsonPath("$[0].ticketsRemaining").isNumber());
    }

    @Test
    void getsABookWithoutExposingTicketContents() throws Exception {
        mockMvc.perform(get("/api/books/" + aBook.bookId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(aBook.bookId().toString()))
                .andExpect(jsonPath("$.totalTickets").isNumber())
                // metadata only — no ticket list / per-ticket prize leaks
                .andExpect(jsonPath("$.tickets").doesNotExist())
                .andExpect(jsonPath("$.bookValue").doesNotExist());
    }

    @Test
    void unknownBookIs404() throws Exception {
        mockMvc.perform(get("/api/books/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
