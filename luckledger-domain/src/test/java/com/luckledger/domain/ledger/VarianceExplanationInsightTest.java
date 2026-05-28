package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VarianceExplanationInsightTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-05-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(TIMESTAMP, ZoneOffset.UTC);

    private final VarianceExplanationInsight generator = new VarianceExplanationInsight(CLOCK);

    @Test
    void shouldReturnEmpty_whenBoughtFromFewerThanThreeBooks() {
        Map<UUID, BookStats> books = new LinkedHashMap<>();
        putBook(books, "0.82");
        putBook(books, "0.41");

        assertThat(generator.evaluate(snapshotWithBooks(books))).isEmpty();
    }

    @Test
    void shouldGenerateInsight_whenBoughtFromThreeBooks() {
        Map<UUID, BookStats> books = new LinkedHashMap<>();
        putBook(books, "0.82");
        putBook(books, "0.65");
        putBook(books, "0.41");

        Optional<Insight> result = generator.evaluate(snapshotWithBooks(books));

        assertThat(result).isPresent();
        Insight insight = result.get();
        assertThat(insight.type()).isEqualTo("VARIANCE_EXPLANATION");
        assertThat(insight.severity()).isEqualTo(InsightSeverity.INFO);
        assertThat(insight.title()).isEqualTo("Natural book variance");
        assertThat(insight.timestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    void shouldContrastHighestAndLowestReturningBooks_inMessageAndData() {
        Map<UUID, BookStats> books = new LinkedHashMap<>();
        UUID highId = putBook(books, "0.82");
        putBook(books, "0.50");
        putBook(books, "0.65");
        UUID lowId = putBook(books, "0.41");

        Insight insight = generator.evaluate(snapshotWithBooks(books)).orElseThrow();

        assertThat(insight.message()).contains("82%").contains("41%");
        assertThat(insight.message()).contains("same pool");
        assertThat(insight.data())
                .containsEntry("bookCount", 4)
                .containsEntry("highestReturnBookId", highId)
                .containsEntry("highestReturnRate", new BigDecimal("0.82"))
                .containsEntry("lowestReturnBookId", lowId)
                .containsEntry("lowestReturnRate", new BigDecimal("0.41"));
    }

    @Test
    void shouldIgnoreBooksWithNoSpend_whenCountingBooksBoughtFrom() {
        Map<UUID, BookStats> books = new LinkedHashMap<>();
        putBook(books, "0.82");
        putBook(books, "0.41");
        // A book that appears in the snapshot but was never actually bought from.
        books.put(UUID.randomUUID(),
                new BookStats(UUID.randomUUID(), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(generator.evaluate(snapshotWithBooks(books))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void shouldReturnEmpty_belowBookThreshold(int bookCount) {
        Map<UUID, BookStats> books = new LinkedHashMap<>();
        for (int i = 0; i < bookCount; i++) {
            putBook(books, "0.50");
        }

        assertThat(generator.evaluate(snapshotWithBooks(books))).isEmpty();
    }

    private static UUID putBook(Map<UUID, BookStats> books, String returnRate) {
        UUID bookId = UUID.randomUUID();
        books.put(bookId, new BookStats(
                bookId,
                5,
                new BigDecimal("100.00"),
                new BigDecimal("100.00").multiply(new BigDecimal(returnRate)),
                new BigDecimal(returnRate)));
        return bookId;
    }

    private static LedgerSnapshot snapshotWithBooks(Map<UUID, BookStats> perBookStats) {
        return new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                perBookStats,
                List.of(),
                List.of());
    }
}
