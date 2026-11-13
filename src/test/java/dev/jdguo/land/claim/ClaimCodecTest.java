package dev.jdguo.land.claim;

import dev.jdguo.land.network.ClaimSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimCodecTest {
    private static final UUID ALICE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID BOB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void roundTripKeepsClaimsMembersNamesAndNegativeCoordinates() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim("minecraft:the_end", -4, 12, ALICE, "爱丽", 8).succeeded());
        Claim claim = map.get("minecraft:the_end", -4, 12);
        claim.setName("市场");
        claim.putFlag("debt", NbtString.of("12"));
        assertTrue(map.trust("minecraft:the_end", -4, 12, ALICE, BOB, "鲍勃").succeeded());

        ClaimMap loaded = ClaimMap.fromNbt(map.toNbt());
        Claim again = loaded.get("minecraft:the_end", -4, 12);
        assertNotNull(again);
        assertEquals(ALICE, again.owner());
        assertEquals("爱丽", again.ownerName());
        assertEquals("市场", again.name());
        assertEquals("12", again.flags().getString("debt"));
        assertTrue(again.isMember(BOB));
        assertEquals("鲍勃", again.members().get(BOB).name());
        assertEquals(map.toNbt(), loaded.toNbt());
    }

    @Test
    void missingOptionalFieldsStillLoad() {
        NbtCompound root = new NbtCompound();
        NbtList claims = new NbtList();
        NbtCompound claim = new NbtCompound();
        claim.putString("Dim", "minecraft:overworld");
        claim.putInt("X", 1);
        claim.putInt("Z", 2);
        claim.putUuid("Owner", ALICE);
        claims.add(claim);
        root.put("Claims", claims);

        Claim loaded = ClaimMap.fromNbt(root).get("minecraft:overworld", 1, 2);
        assertNotNull(loaded);
        assertEquals("", loaded.ownerName());
        assertEquals("", loaded.name());
        assertTrue(loaded.members().isEmpty());
        assertTrue(loaded.flags().isEmpty());
    }

    @Test
    void unknownKeysAndANewerFormatSurviveASave() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim("minecraft:overworld", 0, 0, ALICE, "Alice", 4).succeeded());
        NbtCompound written = map.toNbt();
        written.putInt("Format", 4);
        written.putString("Bank", "later");
        NbtCompound claim = written.getList("Claims", NbtElement.COMPOUND_TYPE).getCompound(0);
        claim.putString("ShopId", "plot-1");
        NbtCompound member = new NbtCompound();
        member.putUuid("Id", BOB);
        member.putString("Name", "Bob");
        member.putInt("Role", 7);
        NbtList members = new NbtList();
        members.add(member);
        claim.put("Members", members);
        claim.getCompound("Flags").putString("shop", "open");

        ClaimMap loaded = ClaimMap.fromNbt(written);
        NbtCompound again = loaded.toNbt();
        assertEquals(4, again.getInt("Format"));
        assertEquals("later", again.getString("Bank"));
        NbtCompound savedClaim = again.getList("Claims", NbtElement.COMPOUND_TYPE).getCompound(0);
        assertEquals("plot-1", savedClaim.getString("ShopId"));
        assertEquals("open", savedClaim.getCompound("Flags").getString("shop"));
        assertEquals(7, savedClaim.getList("Members", NbtElement.COMPOUND_TYPE).getCompound(0).getInt("Role"));
        assertTrue(loaded.get("minecraft:overworld", 0, 0).isMember(BOB));
    }

    @Test
    void brokenEntriesAreSkippedAndTheFirstDuplicateWins() {
        NbtCompound root = new NbtCompound();
        NbtList claims = new NbtList();
        claims.add(new NbtCompound());
        NbtCompound first = new NbtCompound();
        first.putString("Dim", "minecraft:overworld");
        first.putInt("X", 5);
        first.putInt("Z", 5);
        first.putUuid("Owner", ALICE);
        first.putString("OwnerName", "Alice");
        claims.add(first);
        NbtCompound second = first.copy();
        second.putUuid("Owner", BOB);
        second.putString("OwnerName", "Bob");
        claims.add(second);
        root.put("Claims", claims);

        ClaimMap loaded = ClaimMap.fromNbt(root);
        assertEquals(1, loaded.size());
        assertEquals(ALICE, loaded.get("minecraft:overworld", 5, 5).owner());
        assertNull(loaded.get("minecraft:overworld", 0, 0));
    }

    @Test
    void emptyCompoundLoadsAsNoClaims() {
        assertEquals(0, ClaimMap.fromNbt(new NbtCompound()).size());
    }

    @Test
    void snapshotPacketRoundTrip() {
        ClaimMap map = new ClaimMap();
        assertTrue(map.claim("minecraft:overworld", -8, 3, ALICE, "Alice", 4).succeeded());
        map.get("minecraft:overworld", -8, 3).setName("家");
        ClaimSnapshot snapshot = map.toSnapshot();
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        snapshot.write(buf);
        ClaimSnapshot read = ClaimSnapshot.read(buf);
        assertEquals(snapshot.generation(), read.generation());
        assertEquals(1, read.claims().size());
        ClaimSnapshot.PlacedClaim claim = read.claims().get(0);
        assertEquals("minecraft:overworld", claim.dimension());
        assertEquals(-8, claim.chunkX());
        assertEquals(3, claim.chunkZ());
        assertEquals(ALICE, claim.owner());
        assertEquals("Alice", claim.ownerName());
        assertEquals("家", claim.name());
        assertFalse(buf.isReadable());
    }
}
