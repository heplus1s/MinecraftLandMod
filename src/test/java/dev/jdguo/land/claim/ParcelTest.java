package dev.jdguo.land.claim;

import dev.jdguo.land.integration.ClaimGate;
import dev.jdguo.land.integration.ClaimGates;
import dev.jdguo.land.integration.LandEvents;
import dev.jdguo.land.integration.LandListener;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParcelTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @AfterEach
    void resetHooks() {
        ClaimGates.resetForTests();
        LandEvents.resetForTests();
    }

    @Test
    void freshClaimsAreSeparateParcelsUntilJoined() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        assertTrue(map.claim(OVERWORLD, 1, 0, ALICE, "Alice", 8).succeeded());
        Claim left = map.get(OVERWORLD, 0, 0);
        Claim right = map.get(OVERWORLD, 1, 0);
        assertNotEquals(left.parcelId(), right.parcelId());
        assertEquals(2, map.parcelCount());
        assertEquals(1, map.parcelChunkCount(left.parcelId()));
        assertEquals(256L, map.parcelBlockCount(left.parcelId()));

        assertTrue(map.trust(OVERWORLD, 0, 0, ALICE, BOB, "Bob").succeeded());
        assertEquals(ClaimMap.Denial.NOT_ADJACENT, map.claimJoin(OVERWORLD, 3, 0, ALICE, "Alice", 8, left.parcelId()).denial());
        assertEquals(ClaimMap.Denial.NOT_ADJACENT, map.claimJoin(OVERWORLD, 1, 1, ALICE, "Alice", 8, left.parcelId()).denial());
        assertEquals(2, map.size());

        assertTrue(map.claimJoin(OVERWORLD, 0, 1, ALICE, "Alice", 8, left.parcelId()).succeeded());
        assertEquals(left.parcelId(), map.get(OVERWORLD, 0, 1).parcelId());
        assertFalse(map.get(OVERWORLD, 0, 1).isMember(BOB));
        assertFalse(map.allowsChange(OVERWORLD, 0, 16, BOB, false));
        assertTrue(map.allowsChange(OVERWORLD, 0, 0, BOB, false));
        assertEquals(2, map.parcelChunkCount(left.parcelId()));
        assertEquals(512L, map.parcelBlockCount(left.parcelId()));
    }

    @Test
    void joinRespectsDimensionOwnerLimitAndGates() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, -2, -5, ALICE, "Alice", 2).succeeded());
        UUID parcel = map.get(OVERWORLD, -2, -5).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, -1, -5, ALICE, "Alice", 2, parcel).succeeded());
        assertEquals(ClaimMap.Denial.AT_LIMIT, map.claimJoin(OVERWORLD, -3, -5, ALICE, "Alice", 2, parcel).denial());
        assertEquals(2, map.size());

        assertTrue(map.claim(NETHER, 0, 0, BOB, "Bob", 4).succeeded());
        UUID nether = map.get(NETHER, 0, 0).parcelId();
        assertEquals(ClaimMap.Denial.DIFFERENT_DIMENSION, map.claimJoin(NETHER, 4, 4, ALICE, "Alice", 8, parcel).denial());
        assertEquals(ClaimMap.Denial.NOT_OWNER, map.claimJoin(NETHER, 1, 0, ALICE, "Alice", 8, nether).denial());
        assertNull(map.get(NETHER, 4, 4));

        ClaimGates.register(new ClaimGate() {
            @Override
            public boolean allowJoin(UUID playerId, ParcelInfo joined, String dimension, int chunkX, int chunkZ) {
                return false;
            }
        });
        assertEquals(ClaimMap.Denial.GATE, map.claimJoin(OVERWORLD, -2, -4, ALICE, "Alice", 8, parcel).denial());
    }

    @Test
    void adjacentOwnedParcelsAreTheChoicesForANewChunk() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        assertTrue(map.claim(OVERWORLD, 2, 0, ALICE, "Alice", 8).succeeded());
        assertTrue(map.claim(OVERWORLD, 1, 1, BOB, "Bob", 8).succeeded());
        List<ParcelInfo> choices = map.adjacentOwnedParcels(ALICE, OVERWORLD, 1, 0);
        assertEquals(2, choices.size());
        assertTrue(ParcelGeometry.edgeAdjacent(0, 0, 1, 0));
        assertFalse(ParcelGeometry.edgeAdjacent(0, 0, 1, 1));
        assertTrue(ParcelGeometry.edgeAdjacent(-2, -5, -1, -5));
    }

    @Test
    void mergeRequiresASharedEdgeAndKeepsTargetIdentity() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        assertTrue(map.claim(OVERWORLD, 2, 0, ALICE, "Alice", 8).succeeded());
        UUID target = map.get(OVERWORLD, 0, 0).parcelId();
        UUID source = map.get(OVERWORLD, 2, 0).parcelId();
        assertTrue(map.renameParcel(ALICE, target, "家").succeeded());
        assertTrue(map.renameParcel(ALICE, source, "田").succeeded());
        assertTrue(map.trust(OVERWORLD, 0, 0, ALICE, BOB, "Bob").succeeded());
        assertTrue(map.trust(OVERWORLD, 2, 0, ALICE, CARA, "Cara").succeeded());
        assertTrue(map.putParcelFlag(target, "debt", NbtString.of("keep")));
        assertTrue(map.putParcelFlag(source, "debt", NbtString.of("old")));
        assertTrue(map.putParcelFlag(source, "shop", NbtString.of("open")));
        assertEquals(ClaimMap.Denial.NOT_ADJACENT, map.merge(ALICE, target, source).denial());

        assertTrue(map.claimJoin(OVERWORLD, 1, 0, ALICE, "Alice", 8, source).succeeded());
        AtomicInteger merges = new AtomicInteger();
        LandEvents.register(new LandListener() {
            @Override
            public void afterMerge(ParcelInfo merged, UUID removedParcelId) {
                merges.incrementAndGet();
                assertEquals(target, merged.id());
                assertEquals(source, removedParcelId);
                assertEquals(3, merged.chunkCount());
            }
        });
        assertTrue(map.merge(ALICE, target, source).succeeded());
        assertEquals(1, merges.get());
        assertEquals(target, map.get(OVERWORLD, 2, 0).parcelId());
        assertEquals("家", map.get(OVERWORLD, 2, 0).name());
        assertNull(map.info(source));
        assertTrue(map.get(OVERWORLD, 0, 0).isMember(BOB));
        assertTrue(map.get(OVERWORLD, 2, 0).isMember(CARA));
        assertFalse(map.allowsChange(OVERWORLD, 32, 0, BOB, false));
        assertEquals("keep", map.copyParcelFlags(target).getString("debt"));
        assertEquals("open", map.copyParcelFlags(target).getString("shop"));
        NbtCompound absorbed = absorbedRecord(map, target);
        assertEquals("田", absorbed.getString("Name"));
        assertEquals("old", absorbed.getCompound("Flags").getString("debt"));

        assertEquals(ClaimMap.Denial.SAME_PARCEL, map.merge(ALICE, target, target).denial());
        assertTrue(map.claim(NETHER, 0, 0, ALICE, "Alice", 8).succeeded());
        assertEquals(ClaimMap.Denial.DIFFERENT_DIMENSION,
                map.merge(ALICE, target, map.get(NETHER, 0, 0).parcelId()).denial());
    }

    @Test
    void splitCarvesMarkedChunksIntoOneNewParcel() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        UUID original = map.get(OVERWORLD, 0, 0).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, 1, 0, ALICE, "Alice", 8, original).succeeded());
        assertTrue(map.claimJoin(OVERWORLD, 2, 0, ALICE, "Alice", 8, original).succeeded());
        assertTrue(map.claimJoin(OVERWORLD, 3, 0, ALICE, "Alice", 8, original).succeeded());
        assertTrue(map.trust(OVERWORLD, 1, 0, ALICE, BOB, "Bob").succeeded());
        assertTrue(map.renameParcel(ALICE, original, "家").succeeded());

        assertEquals(ClaimMap.Denial.ENTIRE_PARCEL, map.split(ALICE, List.of(
                key(0, 0), key(1, 0), key(2, 0), key(3, 0)), "菜地").denial());
        assertEquals(ClaimMap.Denial.NAME_INVALID, map.split(ALICE, List.of(key(1, 0)), "x".repeat(33)).denial());

        AtomicInteger splits = new AtomicInteger();
        LandEvents.register(new LandListener() {
            @Override
            public void afterSplit(ParcelInfo remainder, ParcelInfo created) {
                splits.incrementAndGet();
                assertEquals(original, remainder.id());
                assertEquals(2, remainder.chunkCount());
                assertEquals(2, created.chunkCount());
                assertEquals("菜地", created.name());
            }
        });
        assertTrue(map.split(ALICE, List.of(key(1, 0), key(2, 0)), "菜地").succeeded());
        assertEquals(1, splits.get());
        assertEquals(original, map.get(OVERWORLD, 0, 0).parcelId());
        assertEquals(original, map.get(OVERWORLD, 3, 0).parcelId());
        assertEquals("家", map.get(OVERWORLD, 0, 0).name());
        UUID carved = map.get(OVERWORLD, 1, 0).parcelId();
        assertEquals(carved, map.get(OVERWORLD, 2, 0).parcelId());
        assertNotEquals(original, carved);
        assertTrue(map.get(OVERWORLD, 1, 0).isMember(BOB));
        assertTrue(map.allowsChange(OVERWORLD, 16, 0, BOB, false));
        assertEquals("菜地", map.info(carved).name());
    }

    @Test
    void chunkTransferDetachesAPieceAndWholeTransferKeepsTheParcel() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        UUID parcel = map.get(OVERWORLD, 0, 0).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, 1, 0, ALICE, "Alice", 8, parcel).succeeded());
        assertTrue(map.trust(OVERWORLD, 0, 0, ALICE, BOB, "Bob").succeeded());
        assertTrue(map.trust(OVERWORLD, 1, 0, ALICE, CARA, "Cara").succeeded());
        assertTrue(map.renameParcel(ALICE, parcel, "家").succeeded());
        map.get(OVERWORLD, 0, 0).putFlag("plot", NbtString.of("west"));

        assertTrue(map.transfer(OVERWORLD, 1, 0, ALICE, BOB, "Bob", 1).succeeded());
        assertEquals(parcel, map.get(OVERWORLD, 0, 0).parcelId());
        assertNotEquals(parcel, map.get(OVERWORLD, 1, 0).parcelId());
        assertEquals(BOB, map.get(OVERWORLD, 1, 0).owner());
        assertEquals("", map.get(OVERWORLD, 1, 0).name());
        assertTrue(map.get(OVERWORLD, 1, 0).members().isEmpty());
        assertFalse(map.allowsChange(OVERWORLD, 16, 0, CARA, false));
        assertFalse(map.allowsChange(OVERWORLD, 16, 0, ALICE, false));
        assertTrue(map.get(OVERWORLD, 0, 0).isMember(BOB));
        assertEquals("west", map.get(OVERWORLD, 0, 0).flags().getString("plot"));
        assertEquals("家", map.get(OVERWORLD, 0, 0).name());

        assertTrue(map.claim(OVERWORLD, 5, 5, CARA, "Cara", 4).succeeded());
        assertTrue(map.claimJoin(OVERWORLD, 6, 5, CARA, "Cara", 4, map.get(OVERWORLD, 5, 5).parcelId()).succeeded());
        UUID estate = map.get(OVERWORLD, 5, 5).parcelId();
        assertEquals(ClaimMap.Denial.AT_LIMIT, map.transferParcel(CARA, estate, ALICE, "Alice", 2, "").denial());
        assertEquals(CARA, map.get(OVERWORLD, 6, 5).owner());

        AtomicInteger sales = new AtomicInteger();
        LandEvents.register(new LandListener() {
            @Override
            public void afterParcelTransfer(ParcelInfo moved, UUID previousOwner, UUID newOwner, String reference) {
                sales.incrementAndGet();
                assertEquals("escrow-9", reference);
                assertEquals(CARA, previousOwner);
                assertEquals(ALICE, newOwner);
                assertEquals(estate, moved.id());
            }
        });
        ClaimGates.register(new ClaimGate() {
            @Override
            public boolean allowParcelTransfer(UUID actorId, ParcelInfo moved, UUID newOwnerId, String reference) {
                return "escrow-9".equals(reference);
            }
        });
        assertEquals(ClaimMap.Denial.GATE, map.transferParcel(CARA, estate, ALICE, "Alice", 8, "").denial());
        assertTrue(map.transferParcel(CARA, estate, ALICE, "Alice", 8, "escrow-9").succeeded());
        assertEquals(1, sales.get());
        assertEquals(estate, map.get(OVERWORLD, 5, 5).parcelId());
        assertEquals(ALICE, map.get(OVERWORLD, 6, 5).owner());
        assertEquals(2, map.parcelChunkCount(estate));
    }

    @Test
    void transferringEveryChunkOfAParcelIsAWholeTransfer() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        UUID parcel = map.get(OVERWORLD, 0, 0).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, 1, 0, ALICE, "Alice", 8, parcel).succeeded());
        assertTrue(map.transferChunks(ALICE, List.of(key(0, 0), key(1, 0), key(0, 0)), BOB, "Bob", 8, "sale").succeeded());
        assertEquals(parcel, map.get(OVERWORLD, 0, 0).parcelId());
        assertEquals(parcel, map.get(OVERWORLD, 1, 0).parcelId());
        assertEquals(BOB, map.info(parcel).owner());
    }

    @Test
    void seizeCanIgnoreTheCapAndCanTakeOneChunk() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        UUID parcel = map.get(OVERWORLD, 0, 0).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, 1, 0, ALICE, "Alice", 8, parcel).succeeded());
        assertTrue(map.trust(OVERWORLD, 0, 0, ALICE, CARA, "Cara").succeeded());
        assertTrue(map.claim(OVERWORLD, 9, 9, BOB, "Bob", 8).succeeded());

        assertEquals(ClaimMap.Denial.AT_LIMIT, map.seizeParcel(parcel, BOB, "Bob", 1, "loan-1").denial());
        assertEquals(ALICE, map.info(parcel).owner());

        AtomicInteger seizures = new AtomicInteger();
        AtomicInteger pieces = new AtomicInteger();
        LandEvents.register(new LandListener() {
            @Override
            public void afterSeizeParcel(ParcelInfo seized, UUID previousOwner, UUID newOwner, String reference) {
                seizures.incrementAndGet();
                assertEquals("loan-1", reference);
                assertEquals(ALICE, previousOwner);
            }

            @Override
            public void afterSeizeChunk(Claim chunk, UUID previousParcelId, ParcelInfo created, UUID previousOwner, UUID newOwner, String reference) {
                pieces.incrementAndGet();
                assertEquals(parcel, previousParcelId);
                assertEquals("loan-1", reference);
            }
        });
        ClaimGates.register(new ClaimGate() {
            @Override
            public boolean allowSeizeParcel(ParcelInfo seized, UUID newOwnerId, String reference) {
                return "loan-1".equals(reference);
            }
        });
        assertEquals(ClaimMap.Denial.GATE, map.seizeChunk(OVERWORLD, 1, 0, BOB, "Bob", 0, "").denial());
        assertTrue(map.seizeChunk(OVERWORLD, 1, 0, BOB, "Bob", 0, "loan-1").succeeded());
        assertEquals(0, seizures.get());
        assertEquals(1, pieces.get());
        assertEquals(parcel, map.get(OVERWORLD, 0, 0).parcelId());
        assertEquals(ALICE, map.get(OVERWORLD, 0, 0).owner());
        assertNotEquals(parcel, map.get(OVERWORLD, 1, 0).parcelId());
        assertFalse(map.allowsChange(OVERWORLD, 16, 0, CARA, false));

        assertTrue(map.seizeParcel(parcel, BOB, "Bob", 0, "loan-1").succeeded());
        assertEquals(1, seizures.get());
        assertEquals(BOB, map.get(OVERWORLD, 0, 0).owner());
        assertTrue(map.get(OVERWORLD, 0, 0).members().isEmpty());
        assertFalse(map.allowsChange(OVERWORLD, 0, 0, ALICE, false));
        assertFalse(map.allowsChange(OVERWORLD, 0, 0, CARA, false));
    }

    @Test
    void aThrowingListenerDoesNotRollBackTheTransfer() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 4).succeeded());
        LandEvents.register(new LandListener() {
            @Override
            public void afterParcelTransfer(ParcelInfo parcel, UUID previousOwner, UUID newOwner, String reference) {
                throw new IllegalStateException("market down");
            }
        });
        assertTrue(map.transfer(OVERWORLD, 0, 0, ALICE, BOB, "Bob", 4).succeeded());
        assertEquals(BOB, map.get(OVERWORLD, 0, 0).owner());
    }

    @Test
    void abandonCanDisconnectAParcelAndDropsItWhenEmpty() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        UUID parcel = map.get(OVERWORLD, 0, 0).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, 1, 0, ALICE, "Alice", 8, parcel).succeeded());
        assertTrue(map.claimJoin(OVERWORLD, 2, 0, ALICE, "Alice", 8, parcel).succeeded());
        assertTrue(map.abandon(OVERWORLD, 1, 0, ALICE).succeeded());
        assertEquals(parcel, map.get(OVERWORLD, 0, 0).parcelId());
        assertEquals(parcel, map.get(OVERWORLD, 2, 0).parcelId());
        assertEquals(2, map.parcelChunkCount(parcel));
        assertTrue(map.abandon(OVERWORLD, 0, 0, ALICE).succeeded());
        assertTrue(map.abandon(OVERWORLD, 2, 0, ALICE).succeeded());
        assertNull(map.info(parcel));
        assertEquals(0, map.parcelCount());
    }

    @Test
    void renameCopiesTheNameOntoEveryChunkAndRejectsControlCharacters() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 4).succeeded());
        UUID parcel = map.get(OVERWORLD, 0, 0).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, 1, 0, ALICE, "Alice", 4, parcel).succeeded());
        assertEquals(ClaimMap.Denial.NAME_INVALID, map.renameParcel(ALICE, parcel, "好\n的").denial());
        assertEquals(ClaimMap.Denial.NAME_INVALID, map.renameParcel(ALICE, parcel, "地".repeat(33)).denial());
        assertTrue(map.renameParcel(ALICE, parcel, "地".repeat(32)).succeeded());
        assertEquals("地".repeat(32), map.get(OVERWORLD, 1, 0).name());
        assertEquals("地".repeat(32), map.info(parcel).name());
        ParcelInfo snapshot = map.info(parcel);
        snapshot.flags().putString("nope", "1");
        assertFalse(map.copyParcelFlags(parcel).contains("nope"));
        assertNull(ParcelNames.clean("a\rb"));
        assertEquals("", ParcelNames.clean("   "));
    }

    @Test
    void phaseOneSavesBecomeOneParcelPerChunkWithoutLosingFields() {
        NbtCompound root = new NbtCompound();
        root.putInt("Format", 1);
        root.putString("Bank", "later");
        NbtList claims = new NbtList();
        claims.add(legacyClaim(0, 0, "市场", true));
        claims.add(legacyClaim(1, 0, "", false));
        root.put("Claims", claims);

        ClaimMap loaded = ClaimMap.fromNbt(root);
        assertTrue(loaded.needsRewrite());
        Claim named = loaded.get(OVERWORLD, 0, 0);
        Claim plain = loaded.get(OVERWORLD, 1, 0);
        assertNotEquals(named.parcelId(), plain.parcelId());
        assertEquals("市场", named.name());
        assertEquals("市场", loaded.info(named.parcelId()).name());
        assertEquals("", plain.name());
        assertEquals("12", named.flags().getString("debt"));
        assertTrue(named.isMember(BOB));
        assertEquals("鲍勃", named.members().get(BOB).name());
        assertEquals(7, memberRole(loaded));

        NbtCompound saved = loaded.toNbt();
        assertTrue(saved.getInt("Format") >= ClaimMap.FORMAT);
        assertEquals("later", saved.getString("Bank"));
        ClaimMap again = ClaimMap.fromNbt(saved);
        assertFalse(again.needsRewrite());
        assertEquals(named.parcelId(), again.get(OVERWORLD, 0, 0).parcelId());
        assertEquals(plain.parcelId(), again.get(OVERWORLD, 1, 0).parcelId());
        assertEquals(saved, again.toNbt());
        assertEquals("plot-1", claimTag(saved, 0).getString("ShopId"));
    }

    @Test
    void aSharedParcelIdWithTwoOwnersIsSplitAndUnknownParcelDataStays() {
        UUID shared = UUID.fromString("abababab-abab-abab-abab-abababababab");
        NbtCompound root = new NbtCompound();
        root.putInt("Format", 2);
        NbtList claims = new NbtList();
        claims.add(claimed(OVERWORLD, 0, 0, shared, ALICE, "Alice"));
        claims.add(claimed(OVERWORLD, 5, 0, shared, BOB, "Bob"));
        claims.add(claimed(NETHER, 0, 0, shared, ALICE, "Alice"));
        root.put("Claims", claims);
        NbtList parcels = new NbtList();
        NbtCompound parcel = new NbtCompound();
        parcel.putUuid("Id", shared);
        parcel.putString("Name", "老地");
        parcel.putString("MarketLock", "escrow-1");
        NbtCompound flags = new NbtCompound();
        flags.putString("collateral", "loan");
        parcel.put("Flags", flags);
        parcels.add(parcel);
        root.put("Parcels", parcels);

        ClaimMap loaded = ClaimMap.fromNbt(root);
        assertEquals(shared, loaded.get(OVERWORLD, 0, 0).parcelId());
        assertEquals("老地", loaded.get(OVERWORLD, 0, 0).name());
        assertEquals("loan", loaded.copyParcelFlags(shared).getString("collateral"));
        assertNotEquals(shared, loaded.get(OVERWORLD, 5, 0).parcelId());
        assertNotEquals(shared, loaded.get(NETHER, 0, 0).parcelId());
        assertNotEquals(loaded.get(OVERWORLD, 5, 0).parcelId(), loaded.get(NETHER, 0, 0).parcelId());
        assertEquals("老地", loaded.get(OVERWORLD, 5, 0).name());
        assertTrue(loaded.copyParcelFlags(loaded.get(OVERWORLD, 5, 0).parcelId()).isEmpty());

        NbtCompound saved = loaded.toNbt();
        NbtCompound stored = parcelTag(saved, shared);
        assertEquals("escrow-1", stored.getString("MarketLock"));
        ClaimMap again = ClaimMap.fromNbt(saved);
        assertEquals(saved, again.toNbt());
    }

    @Test
    void queriesReportChunkAreaForValuation() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 4, -2, ALICE, "Alice", 8).succeeded());
        UUID parcel = map.get(OVERWORLD, 4, -2).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, 5, -2, ALICE, "Alice", 8, parcel).succeeded());
        assertTrue(map.claim(NETHER, 0, 0, ALICE, "Alice", 8).succeeded());
        assertEquals(2, map.parcelChunkCount(parcel));
        assertEquals(512L, map.parcelBlockCount(parcel));
        assertEquals(3, map.countOwned(ALICE));
        assertEquals(2, map.countParcelsOwned(ALICE));
        assertEquals(0, map.parcelChunkCount(UUID.randomUUID()));
        assertEquals(8, map.info(parcel).shortId().length());
    }

    private static ClaimKey key(int x, int z) {
        return new ClaimKey(OVERWORLD, x, z);
    }

    private static NbtCompound legacyClaim(int x, int z, String name, boolean extras) {
        NbtCompound claim = new NbtCompound();
        claim.putString("Dim", OVERWORLD);
        claim.putInt("X", x);
        claim.putInt("Z", z);
        claim.putUuid("Owner", ALICE);
        claim.putString("OwnerName", "爱丽");
        claim.putString("Name", name);
        if (extras) {
            NbtCompound flags = new NbtCompound();
            flags.putString("debt", "12");
            claim.put("Flags", flags);
            claim.putString("ShopId", "plot-1");
            NbtList members = new NbtList();
            NbtCompound member = new NbtCompound();
            member.putUuid("Id", BOB);
            member.putString("Name", "鲍勃");
            member.putInt("Role", 7);
            members.add(member);
            claim.put("Members", members);
        }
        return claim;
    }

    private static int memberRole(ClaimMap map) {
        NbtCompound saved = map.toNbt();
        NbtList claims = saved.getList("Claims", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < claims.size(); i++) {
            NbtCompound claim = claims.getCompound(i);
            if (claim.getInt("X") == 0) {
                return claim.getList("Members", NbtElement.COMPOUND_TYPE).getCompound(0).getInt("Role");
            }
        }
        return -1;
    }

    private static NbtCompound claimed(String dimension, int x, int z, UUID parcel, UUID owner, String ownerName) {
        NbtCompound claim = new NbtCompound();
        claim.putString("Dim", dimension);
        claim.putInt("X", x);
        claim.putInt("Z", z);
        claim.putUuid("Parcel", parcel);
        claim.putUuid("Owner", owner);
        claim.putString("OwnerName", ownerName);
        return claim;
    }

    private static NbtCompound claimTag(NbtCompound root, int x) {
        NbtList claims = root.getList("Claims", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < claims.size(); i++) {
            NbtCompound claim = claims.getCompound(i);
            if (claim.getInt("X") == x) {
                return claim;
            }
        }
        throw new AssertionError("missing claim " + x);
    }

    private static NbtCompound parcelTag(NbtCompound root, UUID id) {
        NbtList parcels = root.getList("Parcels", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < parcels.size(); i++) {
            NbtCompound parcel = parcels.getCompound(i);
            if (id.equals(parcel.getUuid("Id"))) {
                return parcel;
            }
        }
        throw new AssertionError("missing parcel " + id);
    }

    private static NbtCompound absorbedRecord(ClaimMap map, UUID target) {
        NbtList absorbed = parcelTag(map.toNbt(), target).getList("Absorbed", NbtElement.COMPOUND_TYPE);
        assertEquals(1, absorbed.size());
        return absorbed.getCompound(0);
    }
}
