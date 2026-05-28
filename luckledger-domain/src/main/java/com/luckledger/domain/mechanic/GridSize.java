package com.luckledger.domain.mechanic;

public enum GridSize {
    THREE(3),
    FOUR(4),
    FIVE(5);

    private final int dimension;

    GridSize(int dimension) {
        this.dimension = dimension;
    }

    public int dimension() {
        return dimension;
    }
}
