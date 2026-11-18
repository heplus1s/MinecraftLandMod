package dev.jdguo.land.network;

import dev.jdguo.land.trade.TradeRequest;
import net.minecraft.network.PacketByteBuf;

/** 127 is not a legacy action: old servers MUST reject before applying any trade. */
public record ContextTradeEnvelope(TradeRequest request, String token) {
    private static final int MARKER = 127;
    public static void write(PacketByteBuf out, String token, TradeRequest request) {
        if (token == null || token.isBlank() || token.length() > 64) throw new IllegalArgumentException("token");
        out.writeByte(MARKER); out.writeString(token, 64); request.write(out);
    }
    public static ContextTradeEnvelope read(PacketByteBuf in) {
        String token = null;
        if (in.getUnsignedByte(in.readerIndex()) == MARKER) {
            in.readByte(); token = in.readString(64);
            if (token.isBlank()) throw new IllegalArgumentException("token");
        }
        var request = TradeRequest.read(in);
        if (in.isReadable()) throw new IllegalArgumentException("Unexpected trade bytes");
        return new ContextTradeEnvelope(request, token);
    }
}
