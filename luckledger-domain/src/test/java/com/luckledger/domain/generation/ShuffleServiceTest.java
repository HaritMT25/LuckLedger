package com.luckledger.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShuffleServiceTest {

    private final ShuffleService shuffler = new ShuffleService();

    private static List<TicketOutcome> outcomes(int n) {
        List<TicketOutcome> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new TicketOutcome(UUID.randomUUID(), BigDecimal.valueOf(i)));
        }
        return list;
    }

    private static List<UUID> ids(List<TicketOutcome> list) {
        return list.stream().map(TicketOutcome::outcomeId).toList();
    }

    @Test
    void sameSeedProducesSameOrder() {
        List<TicketOutcome> a = outcomes(50);
        List<TicketOutcome> b = new ArrayList<>(a); // identical contents/order

        shuffler.shuffle(a, 12345L);
        shuffler.shuffle(b, 12345L);

        assertThat(ids(a)).containsExactlyElementsOf(ids(b));
    }

    @Test
    void differentSeedsTypicallyProduceDifferentOrders() {
        List<TicketOutcome> a = outcomes(50);
        List<TicketOutcome> b = new ArrayList<>(a);

        shuffler.shuffle(a, 1L);
        shuffler.shuffle(b, 2L);

        assertThat(ids(a)).isNotEqualTo(ids(b));
    }

    @Test
    void shufflePreservesAllElements() {
        List<TicketOutcome> original = outcomes(100);
        List<UUID> before = ids(original);

        shuffler.shuffle(original, 7L);

        assertThat(ids(original)).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void shufflesInPlaceAndReturnsSameInstance() {
        List<TicketOutcome> list = outcomes(10);

        List<TicketOutcome> result = shuffler.shuffle(list, 1L);

        assertThat(result).isSameAs(list);
    }

    @Test
    void singleElementAndEmptyAreNoOps() {
        assertThat(shuffler.shuffle(new ArrayList<>(), 1L)).isEmpty();

        List<TicketOutcome> one = outcomes(1);
        List<UUID> before = ids(one);
        assertThat(ids(shuffler.shuffle(one, 1L))).isEqualTo(before);
    }

    @Test
    void unseededShufflePreservesElements() {
        List<TicketOutcome> list = outcomes(30);
        List<UUID> before = ids(list);

        shuffler.shuffle(list);

        assertThat(ids(list)).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void nullListIsRejected() {
        assertThatThrownBy(() -> shuffler.shuffle(null, 1L)).isInstanceOf(NullPointerException.class);
    }
}
