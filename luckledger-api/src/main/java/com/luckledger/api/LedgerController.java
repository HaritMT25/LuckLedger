package com.luckledger.api;

import com.luckledger.domain.ledger.BookStats;
import com.luckledger.domain.ledger.CurvePoint;
import com.luckledger.domain.ledger.DealerStats;
import com.luckledger.domain.ledger.Insight;
import com.luckledger.domain.ledger.LedgerSnapshot;
import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.player.ledger.LedgerService;
import com.luckledger.player.ledger.TransactionRecorder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public LedgerController(LedgerService ledgerService, TransactionRecorder transactionRecorder) {
        this.ledgerService = ledgerService;
        this.transactionRecorder = transactionRecorder;
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

    @GetMapping("/insights")
    public List<Insight> insights(@PathVariable UUID playerId) {
        return ledgerService.generateInsights(playerId);
    }

    @GetMapping("/dealer-comparison")
    public Map<UUID, DealerStats> dealerComparison(@PathVariable UUID playerId) {
        return ledgerService.getDealerComparison(playerId);
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
