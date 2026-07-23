package com.luckledger.api;

import com.luckledger.domain.ledger.BookStats;
import com.luckledger.domain.ledger.CurvePoint;
import com.luckledger.domain.ledger.DealerStats;
import com.luckledger.domain.ledger.Insight;
import com.luckledger.domain.ledger.LedgerSnapshot;
import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.api.persistence.DealerRepository;
import com.luckledger.player.ledger.LedgerService;
import com.luckledger.player.ledger.TransactionRecorder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only views over a player's append-only ledger: the rolling snapshot, the raw transactions
 * (optionally filtered by type or limited to the most recent), the educational insights, the
 * per-dealer comparison, the inevitability curve, and per-book stats. Unknown players simply yield
 * empty results — the ledger is read-only.
 */
@RestController
@RequestMapping("/api/ledger/{playerId}")
public class LedgerController {

    private final LedgerService ledgerService;
    private final TransactionRecorder transactionRecorder;
    private final DealerRepository dealers;

    public LedgerController(LedgerService ledgerService, TransactionRecorder transactionRecorder,
            DealerRepository dealers) {
        this.ledgerService = ledgerService;
        this.transactionRecorder = transactionRecorder;
        this.dealers = dealers;
    }

    @GetMapping
    public LedgerSnapshot snapshot(@PathVariable UUID playerId) {
        return ledgerService.getSnapshot(playerId);
    }

    @GetMapping("/transactions")
    public List<Transaction> transactions(
            @PathVariable UUID playerId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Integer limit) {
        if (type != null) {
            return transactionRecorder.getTransactions(playerId, type);
        }
        if (limit != null) {
            return transactionRecorder.getRecentTransactions(playerId, limit);
        }
        return transactionRecorder.getTransactions(playerId);
    }

    /**
     * Exports the player's whole ledger as a CSV download — education first: your data is yours to keep.
     * Rows are emitted in the same order as {@code GET /transactions} (the full, unfiltered history) with
     * a fixed header {@code timestamp,type,amount,shop,book,ticket}. The {@code shop} column carries the
     * dealer's real shop name when it resolves (cached per request), otherwise the raw dealer id; an
     * absent dealer/book/ticket becomes an empty field. Anonymous access, consistent with the other
     * ledger reads.
     *
     * @param playerId the player whose ledger to export
     * @return {@code 200} with {@code text/csv} body and an {@code attachment} disposition
     */
    @GetMapping(value = "/transactions.csv", produces = "text/csv; charset=utf-8")
    public ResponseEntity<String> transactionsCsv(@PathVariable UUID playerId) {
        List<Transaction> txns = transactionRecorder.getTransactions(playerId);
        Map<UUID, String> shopNames = new HashMap<>();
        StringBuilder csv = new StringBuilder();
        csv.append("timestamp,type,amount,shop,book,ticket\r\n");
        for (Transaction t : txns) {
            csv.append(escapeCsv(t.timestamp() == null ? "" : t.timestamp().toString())).append(',')
                    .append(escapeCsv(t.type() == null ? "" : t.type().name())).append(',')
                    .append(escapeCsv(t.amount() == null ? "" : t.amount().toPlainString())).append(',')
                    .append(escapeCsv(resolveShop(t.dealerId(), shopNames))).append(',')
                    .append(escapeCsv(t.bookId() == null ? "" : t.bookId().toString())).append(',')
                    .append(escapeCsv(t.ticketId() == null ? "" : t.ticketId().toString())).append("\r\n");
        }
        String filename = "luckledger-" + playerId + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(csv.toString());
    }

    /** The dealer's real shop name when resolvable (memoized per request), else the raw id, else empty. */
    private String resolveShop(UUID dealerId, Map<UUID, String> cache) {
        if (dealerId == null) {
            return "";
        }
        return cache.computeIfAbsent(dealerId, id -> dealers.findById(id)
                .map(DealerEntity::getShopName)
                .orElse(id.toString()));
    }

    /**
     * Escapes a single CSV field per RFC 4180: a field containing a comma, double-quote, or newline is
     * wrapped in double-quotes with any embedded double-quotes doubled. Null becomes the empty field.
     */
    static String escapeCsv(String field) {
        if (field == null) {
            return "";
        }
        if (field.indexOf(',') >= 0 || field.indexOf('"') >= 0
                || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0) {
            return '"' + field.replace("\"", "\"\"") + '"';
        }
        return field;
    }

    @GetMapping("/insights")
    public List<Insight> insights(@PathVariable UUID playerId) {
        return ledgerService.generateInsights(playerId);
    }

    @GetMapping("/dealer-comparison")
    public Map<UUID, DealerStats> dealerComparison(@PathVariable UUID playerId) {
        // LedgerService sees only the ledger, so it names each dealer by its id; swap in the real
        // shop name here where the dealer registry is available.
        Map<UUID, DealerStats> named = new LinkedHashMap<>();
        ledgerService.getDealerComparison(playerId).forEach((id, stats) -> named.put(id,
                dealers.findById(id)
                        .map(DealerEntity::getShopName)
                        .map(name -> new DealerStats(stats.dealerId(), name, stats.ticketsBought(),
                                stats.totalSpent(), stats.totalWon(), stats.returnRate()))
                        .orElse(stats)));
        return named;
    }

    @GetMapping("/curve")
    public List<CurvePoint> curve(@PathVariable UUID playerId) {
        return ledgerService.getInevitabilityCurve(playerId);
    }

    @GetMapping("/books")
    public Map<UUID, BookStats> books(@PathVariable UUID playerId) {
        return ledgerService.getSnapshot(playerId).perBookStats();
    }
}
