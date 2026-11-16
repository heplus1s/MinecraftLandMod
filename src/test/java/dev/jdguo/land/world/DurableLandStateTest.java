package dev.jdguo.land.world;

import java.nio.file.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DurableLandStateTest {
    @TempDir Path directory;
    @Test void ownershipAndReceiptSurviveTogetherAndReceiptSurvivesLaterOwnershipChange() throws Exception {
        Path file = directory.resolve("data/landmod_claims.dat");
        NbtCompound state = new NbtCompound();
        state.putString("testOwner", "buyer");
        NbtCompound receipts = new NbtCompound(); receipts.putString("sale:one", "seller|buyer|parcel|chunks");
        state.put("MarketTransfers", receipts);
        DurableLandState.save(file, state, 3465);
        NbtCompound disk = NbtIo.readCompressed(file.toFile()).getCompound("data");
        assertEquals(state, disk);
        state.putString("testOwner", "third-party");
        DurableLandState.save(file, state, 3465);
        disk = NbtIo.readCompressed(file.toFile()).getCompound("data");
        assertEquals("third-party", disk.getString("testOwner"));
        assertEquals("seller|buyer|parcel|chunks", disk.getCompound("MarketTransfers").getString("sale:one"));
    }
    @Test void unsuccessfulCheckpointThrowsInsteadOfAcknowledgingDelivery() throws Exception {
        Path notDirectory = directory.resolve("file"); Files.writeString(notDirectory, "blocked");
        assertThrows(java.io.IOException.class, () -> DurableLandState.save(notDirectory.resolve("state.dat"), new NbtCompound(), 3465));
        assertEquals("blocked", Files.readString(notDirectory));
    }
}
