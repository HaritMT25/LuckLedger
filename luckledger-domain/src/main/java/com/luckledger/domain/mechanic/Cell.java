package com.luckledger.domain.mechanic;

import java.util.Objects;

public record Cell(Position position, String symbol, double prizeValue) {

    public Cell {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (prizeValue < 0) {
            throw new IllegalArgumentException("prizeValue must be non-negative, was " + prizeValue);
        }
    }
}
