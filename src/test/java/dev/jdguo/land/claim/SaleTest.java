package dev.jdguo.land.claim;

import dev.jdguo.land.integration.ClaimGate;
import dev.jdguo.land.integration.ClaimGates;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @AfterEach
    void reset() {
        ClaimGates.resetForTests();
    }

    @Test
    void parcelSaleTransfersTheWholeParcelAndClearsMembers() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        UUID parcel = map.get(OVERWORLD, 0, 0).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, 1, 0, ALICE, "Alice", 8, parcel).succeeded());
        assertTrue(map.trust(OVERWORLD, 0, 0, ALICE, CARA, "Cara").succeeded());
        assertTrue(map.sellParcel(ALICE, "Alice", parcel).succeeded());
        SaleInfo sale = map.saleOf(parcel);
        assertNotNull(sale);
        assertEquals(SaleKind.PARCEL, sale.kind());
        assertEquals(2, sale.chunks().size());

        AtomicReference<String> reference = new AtomicReference<>();
        ClaimGates.register(new ClaimGate() {
            @Override
            public boolean allowParcelTransfer(UUID actorId, ParcelInfo moved, UUID newOwnerId, String ref) {
                reference.set(ref);
                return ref.startsWith("sale:");
            }
        });
        assertEquals(ClaimMap.Denial.GATE, map.transferParcel(ALICE, parcel, BOB, "Bob", 8, "").denial());
        assertEquals(ClaimMap.Denial.SELF, map.buyParcel(ALICE, "Alice", parcel, 8).denial());
        assertEquals(ClaimMap.Denial.WHOLE_ONLY, map.buyChunks(BOB, "Bob", List.of(new ClaimKey(OVERWORLD, 0, 0)), 8).denial());
        assertTrue(map.buyParcel(BOB, "Bob", parcel, 8).succeeded());
        assertEquals("sale:" + sale.id(), reference.get());
        assertEquals(BOB, map.get(OVERWORLD, 0, 0).owner());
        assertEquals(BOB, map.get(OVERWORLD, 1, 0).owner());
        assertEquals(parcel, map.get(OVERWORLD, 1, 0).parcelId());
        assertTrue(map.get(OVERWORLD, 0, 0).members().isEmpty());
        assertFalse(map.allowsChange(OVERWORLD, 0, 0, CARA, false));
        assertNull(map.saleOf(parcel));
    }

    @Test
    void chunkSaleSplitsOffOnlyTheBoughtChunks() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 8).succeeded());
        UUID parcel = map.get(OVERWORLD, 0, 0).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, 1, 0, ALICE, "Alice", 8, parcel).succeeded());
        assertTrue(map.claimJoin(OVERWORLD, 2, 0, ALICE, "Alice", 8, parcel).succeeded());
        assertTrue(map.trust(OVERWORLD, 1, 0, ALICE, CARA, "Cara").succeeded());
        assertTrue(map.trust(OVERWORLD, 0, 0, ALICE, BOB, "Bob").succeeded());
        assertTrue(map.sellChunks(ALICE, "Alice", List.of(key(1, 0), key(2, 0))).succeeded());
        assertEquals(SaleKind.CHUNKS, map.saleOf(parcel).kind());
        assertEquals(ClaimMap.Denial.NOT_FOR_SALE, map.buyChunks(BOB, "Bob", List.of(key(0, 0)), 8).denial());

        assertTrue(map.buyChunks(BOB, "Bob", List.of(key(1, 0)), 8).succeeded());
        assertEquals(ALICE, map.get(OVERWORLD, 0, 0).owner());
        assertEquals(ALICE, map.get(OVERWORLD, 2, 0).owner());
        assertEquals(BOB, map.get(OVERWORLD, 1, 0).owner());
        assertEquals(parcel, map.get(OVERWORLD, 0, 0).parcelId());
        assertNotEquals(parcel, map.get(OVERWORLD, 1, 0).parcelId());
        assertFalse(map.allowsChange(OVERWORLD, 16, 0, CARA, false));
        assertTrue(map.get(OVERWORLD, 0, 0).isMember(BOB));
        SaleInfo left = map.saleOf(parcel);
        assertNotNull(left);
        assertEquals(List.of(key(2, 0)), left.chunks());

        assertEquals(ClaimMap.Denial.AT_LIMIT, map.buyChunks(BOB, "Bob", List.of(key(2, 0)), 1).denial());
        assertNotNull(map.saleOf(parcel));
        assertTrue(map.buyChunks(BOB, "Bob", List.of(key(2, 0)), 2).succeeded());
        assertNull(map.saleOf(parcel));
    }

    @Test
    void aChangedParcelCancelsTheOffer() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 4, 4, ALICE, "Alice", 8).succeeded());
        UUID parcel = map.get(OVERWORLD, 4, 4).parcelId();
        assertTrue(map.sellParcel(ALICE, "Alice", parcel).succeeded());
        assertTrue(map.claimJoin(OVERWORLD, 5, 4, ALICE, "Alice", 8, parcel).succeeded());
        assertEquals(ClaimMap.Denial.SALE_STALE, map.buyParcel(BOB, "Bob", parcel, 8).denial());
        assertEquals(ALICE, map.get(OVERWORLD, 4, 4).owner());
        assertNull(map.saleOf(parcel));
    }

    @Test
    void saleRoundTripKeepsUnknownKeys() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, -3, 2, ALICE, "Alice", 4).succeeded());
        UUID parcel = map.get(OVERWORLD, -3, 2).parcelId();
        assertTrue(map.sellParcel(ALICE, "Alice", parcel).succeeded());
        NbtCompound written = map.toNbt();
        NbtCompound sale = written.getList("Sales", NbtElement.COMPOUND_TYPE).getCompound(0);
        sale.putString("Note", "later");
        ClaimMap loaded = ClaimMap.fromNbt(written);
        assertEquals(parcel, loaded.saleOf(parcel).parcelId());
        assertEquals("later", loaded.toNbt().getList("Sales", NbtElement.COMPOUND_TYPE).getCompound(0).getString("Note"));
        assertEquals(loaded.toNbt(), ClaimMap.fromNbt(loaded.toNbt()).toNbt());
        assertTrue(loaded.cancelSale(ALICE, parcel).succeeded());
        assertNull(loaded.saleOf(parcel));
        assertEquals(ClaimMap.Denial.NOT_FOR_SALE, loaded.buyParcel(BOB, "Bob", parcel, 4).denial());
    }

    @Test
    void listingEveryChunkIsAParcelSale() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim(OVERWORLD, 0, 0, ALICE, "Alice", 4).succeeded());
        UUID parcel = map.get(OVERWORLD, 0, 0).parcelId();
        assertTrue(map.claimJoin(OVERWORLD, 0, 1, ALICE, "Alice", 4, parcel).succeeded());
        assertTrue(map.sellChunks(ALICE, "Alice", List.of(key(0, 0), key(0, 1))).succeeded());
        assertEquals(SaleKind.PARCEL, map.saleOf(parcel).kind());
    }

    private static ClaimKey key(int x, int z) {
        return new ClaimKey(OVERWORLD, x, z);
    }
}
