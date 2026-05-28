package com.luckledger.domain.mechanic;

import java.util.Objects;

public record NearMissResult(boolean isNearMiss, int distance, String description) {

    public NearMissResult {
        if (distance < 0) {
            throw new IllegalArgumentException("distance must be non-negative, was " + distance);
        }
        Objects.requireNonNull(description, "description must not be null");
    }
}
