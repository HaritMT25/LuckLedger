package com.luckledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.player.ledger.InMemoryTransactionRecorder;
import com.luckledger.player.ledger.TransactionRecorder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
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
        TransactionRecorder recorder = new InMemoryTransactionRecorder();
        record(recorder, TransactionType.BORROW, "100");
        record(recorder, TransactionType.SPEND, "5");
        record(recorder, TransactionType.WIN, "25");
        // No dealer rows exist in this standalone setup; the name-enrichment path sees an empty
        // comparison map, so a default mock (findById -> Optional.empty) is all the controller needs.
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new LedgerController(new ApiConfig().ledgerService(recorder), recorder,
                                org.mockito.Mockito.mock(
                                        com.luckledger.api.persistence.DealerRepository.class)))
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

    @Test
    void exportsTransactionsAsCsvWithHeaderRowAndAttachmentDisposition() throws Exception {
        String body = mockMvc.perform(get("/api/ledger/" + playerId + "/transactions.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=utf-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"luckledger-" + playerId + ".csv\""))
                .andReturn().getResponse().getContentAsString();

        String[] lines = body.split("\r\n");
        // One header row plus exactly one data row per recorded transaction (three: BORROW, SPEND, WIN).
        assertThat(lines).hasSize(4);
        assertThat(lines[0]).isEqualTo("timestamp,type,amount,shop,book,ticket");
        assertThat(body).contains("BORROW", "SPEND", "WIN");
    }

    @Test
    void csvExportEscapesAndResolvesTheRealShopName() throws Exception {
        UUID player = UUID.randomUUID();
        UUID dealerId = UUID.randomUUID();
        TransactionRecorder recorder = new InMemoryTransactionRecorder();
        recorder.record(new Transaction(
                UUID.randomUUID(), player, TransactionType.SPEND, new BigDecimal("5"),
                dealerId, UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        DealerEntity dealer = mock(DealerEntity.class);
        when(dealer.getShopName()).thenReturn("Lucky's \"Corner\", Shop");
        DealerRepository dealers = mock(DealerRepository.class);
        when(dealers.findById(dealerId)).thenReturn(Optional.of(dealer));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new LedgerController(new ApiConfig().ledgerService(recorder), recorder, dealers))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        String body = mvc.perform(get("/api/ledger/" + player + "/transactions.csv"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // A comma-and-quote shop name is wrapped in quotes with embedded quotes doubled (RFC 4180).
        assertThat(body).contains("\"Lucky's \"\"Corner\"\", Shop\"");
    }

    @Test
    void escapeCsvQuotesFieldsContainingCommaQuoteOrNewline() {
        assertThat(LedgerController.escapeCsv(null)).isEmpty();
        assertThat(LedgerController.escapeCsv("plain")).isEqualTo("plain");
        assertThat(LedgerController.escapeCsv("a,b")).isEqualTo("\"a,b\"");
        assertThat(LedgerController.escapeCsv("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(LedgerController.escapeCsv("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(LedgerController.escapeCsv("a\rb")).isEqualTo("\"a\rb\"");
    }
}
