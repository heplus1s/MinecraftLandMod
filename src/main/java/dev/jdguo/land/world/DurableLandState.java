package dev.jdguo.land.world;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

/** Ownership and its delivery receipt are one atomic checkpoint, never separate files. */
final class DurableLandState {
    private DurableLandState() {}
    static void save(Path file, NbtCompound data, int dataVersion) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".transfer.tmp");
        NbtCompound root = new NbtCompound();
        root.put("data", data.copy());
        root.putInt("DataVersion", dataVersion);
        NbtIo.writeCompressed(root, temp.toFile());
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        NbtCompound read = NbtIo.readCompressed(file.toFile());
        if (read == null || !data.equals(read.getCompound("data"))) throw new IOException("land checkpoint mismatch");
    }
}
