package dev.jdguo.land.claim;

import net.minecraft.nbt.NbtCompound;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only picture of one parcel.
 * {@code chunkCount} is how many 16×16 chunks it contains.
 * {@code blockCount} is {@code chunkCount * 256} horizontal block columns, not a volume.
 * {@code flags} is a copy made when this snapshot was built.
 */
public record ParcelInfo(
        UUID id,
        String name,
        UUID owner,
        String ownerName,
        String dimension,
        int chunkCount,
        long blockCount,
        List<ClaimKey> chunks,
        NbtCompound flags
) {
    public static final long BLOCKS_PER_CHUNK = 256L;

    public ParcelInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        name = name == null ? "" : name;
        ownerName = ownerName == null ? "" : ownerName;
        dimension = dimension == null ? "" : dimension;
        chunks = List.copyOf(chunks);
        flags = flags == null ? new NbtCompound() : flags.copy();
    }

    public String shortId() {
        return id.toString().replace("-", "").substring(0, 8);
    }
}
