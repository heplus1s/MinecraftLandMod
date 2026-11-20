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
}
