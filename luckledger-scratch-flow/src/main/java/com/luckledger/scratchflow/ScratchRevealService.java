package com.luckledger.scratchflow;

import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.player.Player;
import com.luckledger.domain.scratch.RevealResult;
import com.luckledger.mechanic.WinEvaluator;
import com.luckledger.player.ledger.TransactionRecorder;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * The reveal half of the scratch flow: a player scratches a previously-purchased ticket. The grid is
 * evaluated; if it wins, the prize is credited and a {@code WIN} is appended to the ledger.
 *
 * <p>Reveal is <strong>idempotent</strong>: the first reveal of a ticket is recorded and cached;
 * subsequent reveals (or {@link #getRevealedResult}) return the cached result without crediting or
 * recording again. This keeps the ledger append-only and prevents double payouts.
 *
 * <p>Not thread-safe: a single player's flow is sequential. The {@code WIN} transaction carries no
 * dealer/book id because the reveal operates on a ticket alone (those fields are nullable).
 */
public final class ScratchRevealService {

    private final WinEvaluator evaluator;
    private final TransactionRecorder transactionRecorder;
    private final Map<UUID, RevealResult> revealedByTicket = new HashMap<>();

    public ScratchRevealService(WinEvaluator evaluator, TransactionRecorder transactionRecorder) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
        this.transactionRecorder =
                Objects.requireNonNull(transactionRecorder, "transactionRecorder must not be null");
    }

    /**
     * Reveals a ticket, crediting the player and recording a WIN if it wins. Idempotent per ticket.
     *
     * @param player the ticket's owner; never {@code null}
     * @param ticket the ticket to reveal; never {@code null}
     * @return the reveal outcome (the cached one if already revealed)
     */
    public RevealResult reveal(Player player, TicketCard ticket) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(ticket, "ticket must not be null");

        UUID ticketId = ticket.ticketId();
        RevealResult cached = revealedByTicket.get(ticketId);
        if (cached != null) {
            return cached; // already revealed — never credit or record twice
        }

        EvaluationResult evaluation = evaluator.evaluate(ticket.layout().grid());
        if (evaluation.isWinner() && evaluation.prizeAmount().signum() > 0) {
            player.credit(evaluation.prizeAmount());
            Transaction win = new Transaction(
                    UUID.randomUUID(),
                    player.getPlayerId(),
                    TransactionType.WIN,
                    evaluation.prizeAmount(),
                    null,
                    null,
                    ticketId,
                    Instant.now());
            transactionRecorder.record(win);
        }

        RevealResult result = new RevealResult(
                ticketId, ticket.skinnedGrid(), evaluation, evaluation.prizeAmount(), evaluation.isWinner());
        revealedByTicket.put(ticketId, result);
        return result;
    }

    /**
     * Returns the stored result for an already-revealed ticket.
     *
     * @param ticketId the revealed ticket's id; never {@code null}
     * @return the stored reveal result
     * @throws NoSuchElementException if the ticket has not been revealed
     */
    public RevealResult getRevealedResult(UUID ticketId) {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        RevealResult result = revealedByTicket.get(ticketId);
        if (result == null) {
            throw new NoSuchElementException("ticket " + ticketId + " has not been revealed");
        }
        return result;
    }
}
