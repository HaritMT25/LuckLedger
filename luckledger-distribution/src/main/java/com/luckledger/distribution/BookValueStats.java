package com.luckledger.distribution;

/**
 * Statistical characterization of the book values within one partition — the spread that lets a
 * dealer allocator rank books and lets the educational ledger explain inter-book variance (why one
 * "lucky store" looks luckier than another even though the pool is fixed).
 *
 * <p>These are descriptive statistics over already-computed book values, so plain {@code double} is
 * used (the underlying book values are summed as {@link java.math.BigDecimal}).
 *
 * @param min the smallest book value in the partition; {@code >= 0}
 * @param max the largest book value; {@code >= min}
 * @param mean the arithmetic mean of the book values; {@code >= 0}
 * @param stddev the population standard deviation of the book values; {@code >= 0}
 * @param median the median book value; {@code >= 0}
 */
public record BookValueStats(double min, double max, double mean, double stddev, double median) {

    public BookValueStats {
        requireFiniteNonNegative(min, "min");
        requireFiniteNonNegative(max, "max");
        requireFiniteNonNegative(mean, "mean");
        requireFiniteNonNegative(stddev, "stddev");
        requireFiniteNonNegative(median, "median");
        if (max < min) {
            throw new IllegalArgumentException("max (" + max + ") must be >= min (" + min + ")");
        }
    }

    private static void requireFiniteNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(field + " must be finite and >= 0, was " + value);
        }
    }
}
