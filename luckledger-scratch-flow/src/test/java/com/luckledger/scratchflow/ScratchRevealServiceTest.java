package com.luckledger.scratchflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.player.Player;
import com.luckledger.domain.scratch.RevealResult;
import com.luckledger.mechanic.WinEvaluator;
import com.luckledger.player.ledger.InMemoryTransactionRecorder;
import com.luckledger.player.ledger.TransactionRecorder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScratchRevealServiceTest {

    private static WinEvaluator evaluatorReturning(boolean winner, String prize) {
        EvaluationResult result = new EvaluationResult(winner, new BigDecimal(prize), List.of(), Map.of());
        return grid -> result;
    }

    @Test
    void revealingAWinnerCreditsAndRecordsAWin() {
        Player player = new Player(UUID.randomUUID(), "P");
        TransactionRecorder recorder = new InMemoryTransactionRecorder();
        ScratchRevealService service = new ScratchRevealService(evaluatorReturning(true, "25"), recorder);
        TicketCard ticket = Fixtures.card(25);

        RevealResult result = service.reveal(player, ticket);

        assertThat(result.isWinner()).isTrue();
        assertThat(result.prizeAmount()).isEqualByComparingTo("25");
        assertThat(player.getCoinBalance()).isEqualByComparingTo("25");
        assertThat(recorder.getTransactions(player.getPlayerId(), TransactionType.WIN)).hasSize(1);
    }

    @Test
    void revealingALoserCreditsNothing() {
        Player player = new Player(UUID.randomUUID(), "P");
        TransactionRecorder recorder = new InMemoryTransactionRecorder();
        ScratchRevealService service = new ScratchRevealService(evaluatorReturning(false, "0"), recorder);

        RevealResult result = service.reveal(player, Fixtures.card(0));

        assertThat(result.isWinner()).isFalse();
        assertThat(player.getCoinBalance()).isEqualByComparingTo("0");
        assertThat(recorder.getTransactions(player.getPlayerId())).isEmpty();
    }

    @Test
    void revealIsIdempotentAndNeverDoubleCredits() {
        Player player = new Player(UUID.randomUUID(), "P");
        TransactionRecorder recorder = new InMemoryTransactionRecorder();
        ScratchRevealService service = new ScratchRevealService(evaluatorReturning(true, "25"), recorder);
        TicketCard ticket = Fixtures.card(25);

        RevealResult first = service.reveal(player, ticket);
        RevealResult second = service.reveal(player, ticket);

        assertThat(second).isSameAs(first);
        assertThat(player.getCoinBalance()).isEqualByComparingTo("25"); // not 50
        assertThat(recorder.getTransactions(player.getPlayerId(), TransactionType.WIN)).hasSize(1);
        assertThat(service.getRevealedResult(ticket.ticketId())).isSameAs(first);
    }

    @Test
    void getRevealedResultForUnrevealedTicketThrows() {
        ScratchRevealService service =
                new ScratchRevealService(evaluatorReturning(false, "0"), new InMemoryTransactionRecorder());

        assertThatThrownBy(() -> service.getRevealedResult(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void nullArgumentsAreRejected() {
        ScratchRevealService service =
                new ScratchRevealService(evaluatorReturning(false, "0"), new InMemoryTransactionRecorder());
        assertThatThrownBy(() -> service.reveal(null, Fixtures.card(0)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.reveal(new Player(UUID.randomUUID(), "P"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
