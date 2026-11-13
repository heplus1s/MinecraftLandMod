package dev.jdguo.land.claim;

import dev.jdguo.land.integration.ClaimGate;
import dev.jdguo.land.integration.ClaimGates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimMapTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @AfterEach
    void resetGates() {
        ClaimGates.resetForTests();
    }

    @Test
    void secondClaimOnTheSameChunkIsRejected() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 3, -2, ALICE, "Alice", 8).succeeded());
        ClaimMap.Result overlap = map.claim(OVERWORLD, 3, -2, BOB, "Bob", 8);
        assertEquals(ClaimMap.Denial.OWNED_BY_OTHER, overlap.denial());
        assertEquals("Alice", overlap.subject());
        assertEquals(ClaimMap.Denial.ALREADY_OWNED, map.claim(OVERWORLD, 3, -2, ALICE, "Alice", 8).denial());
        assertEquals(1, map.size());
    }

    @Test
    void adjacentChunksAndOtherDimensionsDoNotOverlap() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        assertTrue(map.claim(OVERWORLD, 1, 0, BOB, "Bob", 8).succeeded());
        assertTrue(map.claim(NETHER, 0, 0, BOB, "Bob", 8).succeeded());
        assertEquals(ALICE, map.get(OVERWORLD, 0, 0).owner());
        assertEquals(BOB, map.get(OVERWORLD, 1, 0).owner());
        assertEquals(BOB, map.get(NETHER, 0, 0).owner());
        assertNull(map.get(OVERWORLD, 0, 1));
    }

    @Test
    void permissionCoversOwnerMemberStrangerOperatorAndNegativeChunks() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, -1, -2, ALICE, "Alice", 8).succeeded());
        assertTrue(map.trust(OVERWORLD, -1, -2, ALICE, BOB, "Bob").succeeded());

        assertTrue(map.allowsChange(OVERWORLD, -1, -17, ALICE, false));
        assertTrue(map.allowsChange(OVERWORLD, -16, -32, BOB, false));
        assertFalse(map.allowsChange(OVERWORLD, -1, -17, CARA, false));
        assertFalse(map.allowsChange(OVERWORLD, -1, -17, null, false));
        assertTrue(map.allowsChange(OVERWORLD, -1, -17, CARA, true));
        assertTrue(map.allowsChange(OVERWORLD, -17, -17, CARA, false));
        assertTrue(map.allowsChange(OVERWORLD, 0, -17, CARA, false));

        assertTrue(map.claim(NETHER, -1, -2, ALICE, "Alice", 8).succeeded());
        assertFalse(map.allowsChange(NETHER, -1, -17, BOB, false));
        assertTrue(map.allowsChange(NETHER, -1, -17, ALICE, false));
        assertTrue(map.allowsChange(NETHER, 0, -17, null, false));
    }

    @Test
    void memberOfOneChunkCannotBuildInAnotherChunkOfTheSameOwner() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 4, 4, ALICE, "Alice", 8).succeeded());
        assertTrue(map.claim(OVERWORLD, 5, 4, ALICE, "Alice", 8).succeeded());
        assertTrue(map.trust(OVERWORLD, 4, 4, ALICE, BOB, "Bob").succeeded());
        assertTrue(map.allowsChange(OVERWORLD, 64, 64, BOB, false));
        assertFalse(map.allowsChange(OVERWORLD, 80, 64, BOB, false));
    }

    @Test
    void claimLimitCountsOwnedChunksAndAbandonFreesASlot() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 2).succeeded());
        assertTrue(map.claim(NETHER, 1, 1, ALICE, "Alice", 2).succeeded());
        assertEquals(ClaimMap.Denial.AT_LIMIT, map.claim(OVERWORLD, 2, 2, ALICE, "Alice", 2).denial());
        assertTrue(map.abandon(NETHER, 1, 1, ALICE).succeeded());
        assertTrue(map.claim(OVERWORLD, 2, 2, ALICE, "Alice", 2).succeeded());
        assertEquals(2, map.countOwned(ALICE));
    }

    @Test
    void nonPositiveLimitIsUnlimited() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 0).succeeded());
        assertTrue(map.claim(OVERWORLD, 1, 0, ALICE, "Alice", -1).succeeded());
    }

    @Test
    void transferMovesOwnershipClearsMembersAndRespectsTheNewOwnersLimit() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 2, 3, ALICE, "Alice", 4).succeeded());
        assertTrue(map.trust(OVERWORLD, 2, 3, ALICE, CARA, "Cara").succeeded());
        assertTrue(map.claim(OVERWORLD, 0, 0, BOB, "Bob", 1).succeeded());

        assertEquals(ClaimMap.Denial.AT_LIMIT, map.transfer(OVERWORLD, 2, 3, ALICE, BOB, "Bob", 1).denial());
        assertTrue(map.allowsChange(OVERWORLD, 32, 48, CARA, false));

        assertTrue(map.abandon(OVERWORLD, 0, 0, BOB).succeeded());
        assertTrue(map.transfer(OVERWORLD, 2, 3, ALICE, BOB, "Bob", 1).succeeded());
        Claim claim = map.get(OVERWORLD, 2, 3);
        assertEquals(BOB, claim.owner());
        assertEquals("Bob", claim.ownerName());
        assertTrue(claim.members().isEmpty());
        assertFalse(map.allowsChange(OVERWORLD, 32, 48, CARA, false));
        assertFalse(map.allowsChange(OVERWORLD, 32, 48, ALICE, false));
        assertTrue(map.allowsChange(OVERWORLD, 32, 48, BOB, false));
        assertEquals(ClaimMap.Denial.NOT_OWNER, map.abandon(OVERWORLD, 2, 3, ALICE).denial());
    }

    @Test
    void trustAndUntrustStayOnThatChunk() {
        ClaimMap map = new ClaimMap();
        assertEquals(ClaimMap.Denial.NOT_CLAIMED, map.trust(OVERWORLD, 1, 1, ALICE, BOB, "Bob").denial());
        assertTrue(map.claim(OVERWORLD, 1, 1, ALICE, "Alice", 4).succeeded());
        assertEquals(ClaimMap.Denial.NOT_OWNER, map.trust(OVERWORLD, 1, 1, BOB, CARA, "Cara").denial());
        assertEquals(ClaimMap.Denial.SELF, map.trust(OVERWORLD, 1, 1, ALICE, ALICE, "Alice").denial());
        assertTrue(map.trust(OVERWORLD, 1, 1, ALICE, BOB, "Bob").succeeded());
        assertEquals(ClaimMap.Denial.ALREADY_MEMBER, map.trust(OVERWORLD, 1, 1, ALICE, BOB, "Bob").denial());
        assertEquals(ClaimMap.Denial.NOT_MEMBER, map.untrust(OVERWORLD, 1, 1, ALICE, CARA, "Cara").denial());
        assertTrue(map.untrust(OVERWORLD, 1, 1, ALICE, BOB, "Bob").succeeded());
        assertFalse(map.get(OVERWORLD, 1, 1).isMember(BOB));
    }

    @Test
    void gateCanVetoClaimAbandonTrustAndTransfer() {
        ClaimMap map = new ClaimMap();
        ClaimGates.register(new ClaimGate() {
            @Override
            public boolean allowClaim(UUID playerId, String dimension, int chunkX, int chunkZ) {
                return chunkX != 9;
            }

            @Override
            public boolean allowAbandon(UUID actorId, Claim claim) {
                return claim.chunkX() != 1;
            }

            @Override
            public boolean allowTrust(UUID actorId, Claim claim, UUID memberId) {
                return false;
            }

            @Override
            public boolean allowTransfer(UUID actorId, Claim claim, UUID newOwnerId) {
                return false;
            }
        });
        assertEquals(ClaimMap.Denial.GATE, map.claim(OVERWORLD, 9, 0, ALICE, "Alice", 8).denial());
        assertTrue(map.claim(OVERWORLD, 1, 0, ALICE, "Alice", 8).succeeded());
        assertEquals(ClaimMap.Denial.GATE, map.abandon(OVERWORLD, 1, 0, ALICE).denial());
        assertEquals(ClaimMap.Denial.GATE, map.trust(OVERWORLD, 1, 0, ALICE, BOB, "Bob").denial());
        assertEquals(ClaimMap.Denial.GATE, map.transfer(OVERWORLD, 1, 0, ALICE, BOB, "Bob", 8).denial());
        assertEquals(ALICE, map.get(OVERWORLD, 1, 0).owner());
    }

    @Test
    void updatingAKnownNameBumpsTheRevisionUsedByMapSync() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 4).succeeded());
        assertTrue(map.trust(OVERWORLD, 0, 0, ALICE, BOB, "Bob").succeeded());
        int revision = map.revision();
        assertFalse(map.updateName(ALICE, "Alice"));
        assertTrue(map.updateName(ALICE, "Alice2"));
        assertEquals("Alice2", map.get(OVERWORLD, 0, 0).ownerName());
        assertTrue(map.revision() > revision);
        assertTrue(map.updateName(BOB, "Robert"));
        assertEquals("Robert", map.get(OVERWORLD, 0, 0).members().get(BOB).name());
    }
}
