package com.luckledger.distribution;

import com.luckledger.domain.generation.TicketCard;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Deals an already-shuffled ticket list into ordered books (Subsystem 7). The partitioner never
 * re-shuffles — tickets arrive pre-shuffled from the generation pipeline, so seeding the shuffle is
 * independent of partition logic (SRP). It only splits the list into {@code bookCount} sequential
 * chunks and summarizes the resulting value spread.
 *
 * <p>Tickets are dealt as contiguous chunks in input order; when the count does not divide evenly,
 * the first {@code n % bookCount} books take one extra ticket. Every input ticket lands in exactly
 * one book, and each book preserves input order so it can be sold sequentially.
 *
 * <p>DESIGN spells the method as {@code partition(List<TicketCard>, int)}, but a {@link TicketBook}
 * must reference the pool it came from; since {@code PoolContract} carries no id, the pool id is an
 * explicit parameter here.
 */
public final class BookPartitioner {

    /**
     * Partitions the tickets into books.
     *
     * @param tickets the pre-shuffled tickets; never {@code null}, non-empty, no null elements
     * @param bookCount how many books to deal into; {@code 1..tickets.size()} (no empty books)
     * @param poolContractId the pool these tickets were generated from; never {@code null}
     * @return the books plus their value-spread statistics
     */
    public PartitionResult partition(List<TicketCard> tickets, int bookCount, UUID poolContractId) {
        Objects.requireNonNull(tickets, "tickets must not be null");
        Objects.requireNonNull(poolContractId, "poolContractId must not be null");
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("tickets must not be empty");
        }
        tickets.forEach(t -> Objects.requireNonNull(t, "tickets must not contain null elements"));
        if (bookCount < 1 || bookCount > tickets.size()) {
            throw new IllegalArgumentException(
                    "bookCount must be in [1, " + tickets.size() + "], was " + bookCount);
        }

        int base = tickets.size() / bookCount;
        int remainder = tickets.size() % bookCount;
        List<TicketBook> books = new ArrayList<>(bookCount);
        int cursor = 0;
        for (int i = 0; i < bookCount; i++) {
            int size = base + (i < remainder ? 1 : 0);
            List<TicketCard> chunk = tickets.subList(cursor, cursor + size);
            books.add(new TicketBook(UUID.randomUUID(), chunk, poolContractId));
            cursor += size;
        }
        return new PartitionResult(books, computeStats(books));
    }

    private static BookValueStats computeStats(List<TicketBook> books) {
        double[] values = books.stream().mapToDouble(b -> b.getBookValue().doubleValue()).toArray();
        Arrays.sort(values);

        double min = values[0];
        double max = values[values.length - 1];
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        double mean = sum / values.length;
        double variance = 0.0;
        for (double v : values) {
            double diff = v - mean;
            variance += diff * diff;
        }
        double stddev = Math.sqrt(variance / values.length);
        double median = values.length % 2 == 1
                ? values[values.length / 2]
                : (values[values.length / 2 - 1] + values[values.length / 2]) / 2.0;

        return new BookValueStats(min, max, mean, stddev, median);
    }
}
