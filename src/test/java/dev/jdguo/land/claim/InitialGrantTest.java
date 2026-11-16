package dev.jdguo.land.claim;

import dev.jdguo.land.integration.ClaimGate;
import dev.jdguo.land.integration.ClaimGates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InitialGrantTest {
    private final UUID buyer = UUID.randomUUID();
    private static ClaimKey key(int x) { return new ClaimKey("minecraft:overworld", x, 0); }
    @AfterEach void reset() { ClaimGates.resetForTests(); }
    @Test void wholeSelectionCommitsAsOneParcel() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.initialGrant(List.of(key(1), key(2)), buyer, "Buyer", 3).succeeded());
        assertEquals(2, map.countOwned(buyer));
        assertEquals(1, map.countParcelsOwned(buyer));
        assertEquals(map.get(key(1).dimension(), 1, 0).parcelId(), map.get(key(2).dimension(), 2, 0).parcelId());
        assertEquals(2, ClaimMap.fromNbt(map.toNbt()).countOwned(buyer));
    }
    @Test void occupiedLastChunkDoesNotPartiallyGrantFirst() {
        ClaimMap map = new ClaimMap();
        map.claim(key(2).dimension(), 2, 0, UUID.randomUUID(), "Other", 3);
        assertFalse(map.initialGrant(List.of(key(1), key(2)), buyer, "Buyer", 3).succeeded());
        assertNull(map.get(key(1).dimension(), 1, 0));
        assertEquals(0, map.countOwned(buyer));
    }
    @Test void limitsAndDuplicateCoordinatesFailBeforeMutation() {
        ClaimMap map = new ClaimMap();
        assertFalse(map.initialGrant(List.of(key(1), key(2)), buyer, "Buyer", 1).succeeded());
        assertFalse(map.initialGrant(List.of(key(1), key(1)), buyer, "Buyer", 3).succeeded());
        assertEquals(0, map.size());
    }
    @Test void gateVetoOnLastChunkDoesNotPartiallyGrant() {
        ClaimGates.register(new ClaimGate() {
            @Override public boolean allowClaim(UUID player, String dimension, int x, int z) { return x != 2; }
        });
        ClaimMap map = new ClaimMap();
        assertFalse(map.initialGrant(List.of(key(1), key(2)), buyer, "Buyer", 3).succeeded());
        assertEquals(0, map.size());
    }
}
