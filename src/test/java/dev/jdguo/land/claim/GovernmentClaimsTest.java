package dev.jdguo.land.claim;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GovernmentClaimsTest {
    private static final String WORLD = "minecraft:overworld";

    @Test void serverAllocationDoesNotReplacePrivateLandOrPartiallyGrantBatch() {
        var map = new ClaimMap(); UUID privateOwner = UUID.randomUUID();
        assertTrue(map.claim(WORLD, 1, 0, privateOwner, "Private", 32).succeeded());
        var selected = List.of(new ClaimKey(WORLD, 0, 0), new ClaimKey(WORLD, 1, 0));
        assertFalse(map.initialGrant(selected, GovernmentOwner.ID, GovernmentOwner.NAME, 0).succeeded());
        assertNull(map.get(WORLD, 0, 0));
        assertEquals(privateOwner, map.get(WORLD, 1, 0).owner());
    }

    @Test void serverOwnerLabelStaysCanonicalAndNoMemberIsTreatedAsPublic() {
        var map = new ClaimMap();
        var result = map.initialGrant(List.of(new ClaimKey(WORLD, 0, 0)), GovernmentOwner.ID, "unused", 0);
        assertTrue(result.succeeded());
        assertEquals(GovernmentOwner.NAME, result.claim().ownerName());
        assertFalse(map.updateName(GovernmentOwner.ID, "Replacement"));
        assertEquals(GovernmentOwner.NAME, map.parcelsOwnedBy(GovernmentOwner.ID).get(0).ownerName());
        assertTrue(result.claim().members().isEmpty());
        assertFalse(map.allowsChange(WORLD, 0, 0, UUID.randomUUID(), false));
    }

    @Test void releasePrechecksAllClaimsAndNeverPartiallyMutates() {
        var map = new ClaimMap();
        var a = new ClaimKey(WORLD, 0, 0); var b = new ClaimKey(WORLD, 1, 0);
        UUID owner = GovernmentOwner.ID;
        assertTrue(map.initialGrant(List.of(a,b), owner, GovernmentOwner.NAME, 0).succeeded());
        assertTrue(map.releaseGovernment(List.of(a,b), false).succeeded());
        assertEquals(2, map.size());
        var guard = new dev.jdguo.land.integration.ClaimGate() {
            @Override public boolean allowAbandon(UUID actor, Claim claim) { return !claim.key().equals(b); }
        };
        dev.jdguo.land.integration.ClaimGates.register(guard);
        try {
            assertFalse(map.releaseGovernment(List.of(a,b), true).succeeded());
            assertEquals(2, map.size());
            assertTrue(map.releaseGovernment(List.of(a),true).succeeded());
            assertEquals(1, map.size());
        } finally {
            dev.jdguo.land.integration.ClaimGates.resetForTests();
        }
    }

    @Test void splitAndMergePreviewNeverMutateAndUseExistingConditions() {
        var map = new ClaimMap();
        var a = new ClaimKey(WORLD, 0, 0); var b = new ClaimKey(WORLD, 1, 0);
        UUID owner = GovernmentOwner.ID;
        var first = map.initialGrant(List.of(a,b), owner, GovernmentOwner.NAME, 0).claim().parcelId();
        assertEquals(ClaimMap.Denial.ENTIRE_PARCEL, map.split(owner, List.of(a,b), "", false).denial());
        assertTrue(map.split(owner, List.of(b), "新宗地", false).succeeded());
        assertEquals(first, map.get(WORLD,1,0).parcelId());
        var second = map.split(owner, List.of(b), "新宗地", true).claim().parcelId();
        assertTrue(map.merge(owner, first, second, false).succeeded());
        assertEquals(second, map.get(WORLD,1,0).parcelId());
        assertTrue(map.merge(owner, first, second, true).succeeded());
        assertEquals(first, map.get(WORLD,1,0).parcelId());
        assertNull(map.info(second));
    }

    @Test void releaseCannotTouchPrivateOrMixedServerParcels() {
        var map = new ClaimMap();
        var a = new ClaimKey(WORLD, 0, 0); var b = new ClaimKey(WORLD, 1, 0);
        assertTrue(map.initialGrant(List.of(a), GovernmentOwner.ID, GovernmentOwner.NAME, 0).succeeded());
        assertTrue(map.initialGrant(List.of(b), UUID.randomUUID(), "Player", 32).succeeded());
        assertEquals(ClaimMap.Denial.NOT_OWNER, map.releaseGovernment(List.of(a,b), true).denial());
        assertEquals(2, map.size());
    }
}
