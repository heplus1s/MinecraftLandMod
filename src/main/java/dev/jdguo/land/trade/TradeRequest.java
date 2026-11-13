package dev.jdguo.land.trade;

import dev.jdguo.land.claim.ClaimKey;
import net.minecraft.network.PacketByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One map or chest-free purchase request. Command words are not involved.
 * The action byte is English-free numeric protocol, not player-facing text.
 */
public record TradeRequest(Action action, @Nullable UUID parcelId, List<ClaimKey> chunks) {
    public static final int MAX_CHUNKS = 512;

    public TradeRequest {
        chunks = List.copyOf(chunks);
    }

    public enum Action {
        BUY_PARCEL,
        BUY_CHUNKS,
        SELL_PARCEL,
        SELL_CHUNKS,
        CANCEL
    }

    public static TradeRequest buyParcel(UUID parcelId) {
        return new TradeRequest(Action.BUY_PARCEL, parcelId, List.of());
    }

    public static TradeRequest buyChunks(List<ClaimKey> chunks) {
        return new TradeRequest(Action.BUY_CHUNKS, null, chunks);
    }

    public static TradeRequest sellParcel(UUID parcelId) {
        return new TradeRequest(Action.SELL_PARCEL, parcelId, List.of());
    }

    public static TradeRequest sellChunks(List<ClaimKey> chunks) {
        return new TradeRequest(Action.SELL_CHUNKS, null, chunks);
    }

    public static TradeRequest cancel(UUID parcelId) {
        return new TradeRequest(Action.CANCEL, parcelId, List.of());
    }

    public void write(PacketByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeBoolean(parcelId != null);
        if (parcelId != null) {
            buf.writeUuid(parcelId);
        }
        buf.writeVarInt(chunks.size());
        for (ClaimKey key : chunks) {
            buf.writeString(key.dimension(), 256);
            buf.writeInt(key.chunkX());
            buf.writeInt(key.chunkZ());
        }
    }

    public static TradeRequest read(PacketByteBuf buf) {
        int ordinal = buf.readByte();
        if (ordinal < 0 || ordinal >= Action.values().length) {
            throw new IllegalArgumentException("Unknown land trade action " + ordinal);
        }
        UUID parcelId = buf.readBoolean() ? buf.readUuid() : null;
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CHUNKS) {
            throw new IllegalArgumentException("Unreasonable land trade chunk count " + count);
        }
        List<ClaimKey> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(new ClaimKey(buf.readString(256), buf.readInt(), buf.readInt()));
        }
        return new TradeRequest(Action.values()[ordinal], parcelId, chunks);
    }
}
