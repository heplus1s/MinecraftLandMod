package dev.jdguo.land.claim;

import java.util.Objects;

public record ClaimKey(String dimension, int chunkX, int chunkZ) {
    public ClaimKey {
        Objects.requireNonNull(dimension, "dimension");
    }
}
