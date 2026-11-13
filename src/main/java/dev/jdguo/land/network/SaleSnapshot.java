package dev.jdguo.land.network;

import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.SaleInfo;
import dev.jdguo.land.claim.SaleKind;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Full list of land currently offered for sale. Only clients that register
 * {@code landmod:sales} receive it. Vanilla clients never do.
 */
public record SaleSnapshot(int revision, List<SaleInfo> sales) {
    public static final int PROTOCOL = 1;
    private static final int MAX_SALES = 100_000;
    private static final int MAX_CHUNKS = 10_000;

    public SaleSnapshot {
        sales = List.copyOf(sales);
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(PROTOCOL);
        buf.writeVarInt(revision);
        buf.writeVarInt(sales.size());
        for (SaleInfo sale : sales) {
            buf.writeByte(sale.kind() == SaleKind.PARCEL ? 0 : 1);
            buf.writeUuid(sale.id());
            buf.writeUuid(sale.parcelId());
            buf.writeUuid(sale.seller());
            buf.writeString(sale.sellerName());
            buf.writeVarInt(sale.chunks().size());
            for (ClaimKey key : sale.chunks()) {
                buf.writeString(key.dimension());
                buf.writeInt(key.chunkX());
                buf.writeInt(key.chunkZ());
            }
        }
    }

    public static SaleSnapshot read(PacketByteBuf buf) {
        int protocol = buf.readVarInt();
        if (protocol != PROTOCOL) {
            throw new IllegalArgumentException("Unsupported land sale packet protocol " + protocol);
        }
        int revision = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_SALES) {
            throw new IllegalArgumentException("Unreasonable land sale count " + count);
        }
        List<SaleInfo> sales = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SaleKind kind = buf.readByte() == 0 ? SaleKind.PARCEL : SaleKind.CHUNKS;
            UUID id = buf.readUuid();
            UUID parcelId = buf.readUuid();
            UUID seller = buf.readUuid();
            String sellerName = buf.readString();
            int chunks = buf.readVarInt();
            if (chunks < 0 || chunks > MAX_CHUNKS) {
                throw new IllegalArgumentException("Unreasonable land sale chunk count " + chunks);
            }
            List<ClaimKey> keys = new ArrayList<>(chunks);
            for (int chunk = 0; chunk < chunks; chunk++) {
                keys.add(new ClaimKey(buf.readString(), buf.readInt(), buf.readInt()));
            }
            sales.add(new SaleInfo(id, kind, parcelId, seller, sellerName, keys));
        }
        return new SaleSnapshot(revision, sales);
    }
}
