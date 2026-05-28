package com.luckledger.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.player.ledger.TransactionRecorder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LedgerControllerTest {

    private MockMvc mockMvc;
    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TransactionRecorder recorder = new TransactionRecorder();
        record(recorder, TransactionType.BORROW, "100");
        record(recorder, TransactionType.SPEND, "5");
        record(recorder, TransactionType.WIN, "25");
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new LedgerController(new ApiConfig().ledgerService(recorder), recorder))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void record(TransactionRecorder recorder, TransactionType type, String amount) {
        recorder.record(new Transaction(
                UUID.randomUUID(), playerId, type, new BigDecimal(amount), null, null, null, Instant.now()));
    }

    @Test
    void snapshotReflectsTheLedger() throws Exception {
        mockMvc.perform(get("/api/ledger/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBorrowed").value(100))
                .andExpect(jsonPath("$.totalSpent").value(5))
                .andExpect(jsonPath("$.totalWon").value(25));
    }

    @Test
    void listsAllTransactions() throws Exception {
        mockMvc.perform(get("/api/ledger/" + playerId + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void filtersTransactionsByType() throws Exception {
        mockMvc.perform(get("/api/ledger/" + playerId + "/transactions").param("type", "SPEND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("SPEND"));
    }

    @Test
    void exposesInsightsComparisonCurveAndBooks() throws Exception {
        mockMvc.perform(get("/api/ledger/" + playerId + "/insights")).andExpect(status().isOk());
        mockMvc.perform(get("/api/ledger/" + playerId + "/dealer-comparison")).andExpect(status().isOk());
        mockMvc.perform(get("/api/ledger/" + playerId + "/curve")).andExpect(status().isOk());
        mockMvc.perform(get("/api/ledger/" + playerId + "/books")).andExpect(status().isOk());
    }
}
