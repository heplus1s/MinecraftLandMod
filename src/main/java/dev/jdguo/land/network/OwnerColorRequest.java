package dev.jdguo.land.network;

import dev.jdguo.land.color.OwnerColorPalette;
import net.minecraft.network.PacketByteBuf;

/** Deliberately contains no owner identity: the server always uses the authenticated sender. */
public record OwnerColorRequest(int operation, int rgb, java.util.UUID session) {
    public static OwnerColorRequest read(PacketByteBuf buf) {
        int operation = buf.readVarInt();
        if (operation < 0 || operation > 2) throw new IllegalArgumentException("operation");
        int rgb = operation == 1 ? buf.readInt() : 0;
        java.util.UUID session = buf.readUuid();
        if ((operation == 1 && !OwnerColorPalette.validRgb(rgb)) || buf.isReadable())
            throw new IllegalArgumentException("color/trailing payload");
        return new OwnerColorRequest(operation, rgb, session);
    }
}
