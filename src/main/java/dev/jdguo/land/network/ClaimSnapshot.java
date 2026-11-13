package dev.jdguo.land.network;

import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Full claim picture sent to clients that have this mod, so a map overlay can draw it.
 * Vanilla clients never receive the packet.
 */
public record ClaimSnapshot(int generation, List<PlacedClaim> claims) {
    public static final int PROTOCOL = 2;
    public static final ClaimSnapshot EMPTY = new ClaimSnapshot(0, List.of());
    private static final int MAX_CLAIMS = 1_000_000;

    public ClaimSnapshot {
        claims = List.copyOf(claims);
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(PROTOCOL);
        buf.writeVarInt(generation);
        buf.writeVarInt(claims.size());
        for (PlacedClaim claim : claims) {
            buf.writeString(claim.dimension());
            buf.writeInt(claim.chunkX());
            buf.writeInt(claim.chunkZ());
            buf.writeUuid(claim.owner());
            buf.writeString(claim.ownerName());
            buf.writeString(claim.name());
            buf.writeUuid(claim.parcelId());
        }
    }

    public static ClaimSnapshot read(PacketByteBuf buf) {
        int protocol = buf.readVarInt();
        if (protocol != PROTOCOL) {
            throw new IllegalArgumentException("Unsupported land claim packet protocol " + protocol);
        }
        int generation = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CLAIMS) {
            throw new IllegalArgumentException("Unreasonable land claim count " + count);
        }
        List<PlacedClaim> claims = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            claims.add(new PlacedClaim(
                    buf.readString(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUuid(),
                    buf.readString(),
                    buf.readString(),
                    buf.readUuid()));
        }
        return new ClaimSnapshot(generation, claims);
    }

    public record PlacedClaim(String dimension, int chunkX, int chunkZ, UUID owner, String ownerName, String name, UUID parcelId) {
    }
}
