package com.luckledger.domain.mechanic;

public record Position(int row, int col) {

    public Position {
        if (row < 0) {
            throw new IllegalArgumentException("row must be non-negative, was " + row);
        }
        if (col < 0) {
            throw new IllegalArgumentException("col must be non-negative, was " + col);
        }
    }
}
