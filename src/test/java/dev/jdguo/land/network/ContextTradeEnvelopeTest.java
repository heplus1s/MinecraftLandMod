package dev.jdguo.land.network;

import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.trade.TradeRequest;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContextTradeEnvelopeTest {
    private TradeRequest request() { return TradeRequest.buyChunks(List.of(new ClaimKey("minecraft:overworld", 4, -3))); }
    private PacketByteBuf buffer() { return new PacketByteBuf(Unpooled.buffer()); }
    @Test void filteredNewChannelsStillCarryBoundTradeOnExistingChannel() {
        var b = buffer();
        try {
            ContextTradeEnvelope.write(b, "lease-token", request());
            var received = ContextTradeEnvelope.read(b);
            assertEquals(request(), received.request()); assertEquals("lease-token", received.token());
        } finally { b.release(); }
    }
    @Test void oldServerRejectsEnvelopeBeforeAnyTradeEvenAfterProxySwitch() {
        var b = buffer();
        try {
            ContextTradeEnvelope.write(b, "previous-server-token", request());
            // Actual unchanged v1.0.10/older legacy decoder, not a mock parser.
            assertThrows(IllegalArgumentException.class, () -> TradeRequest.read(b));
        } finally { b.release(); }
    }
    @Test void oldClientPacketStillDecodesWithoutInventingAToken() {
        var b = buffer();
        try {
            request().write(b);
            var received = ContextTradeEnvelope.read(b);
            assertEquals(request(), received.request()); assertNull(received.token());
        } finally { b.release(); }
    }
    @Test void malformedOrMissingTokenCannotBecomeALegacyTrade() {
        var b = buffer();
        try {
            b.writeByte(127); b.writeString(""); request().write(b);
            assertThrows(IllegalArgumentException.class, () -> ContextTradeEnvelope.read(b));
            b.clear(); b.writeByte(127);
            assertThrows(RuntimeException.class, () -> ContextTradeEnvelope.read(b));
            b.clear(); ContextTradeEnvelope.write(b, "token", request()); b.writeByte(1);
            assertThrows(IllegalArgumentException.class, () -> ContextTradeEnvelope.read(b));
            assertThrows(IllegalArgumentException.class, () -> ContextTradeEnvelope.write(b, " ", request()));
        } finally { b.release(); }
    }
    @Test void negotiatedConnectionDoesNotBecomeLegacyWhenLeaseExpires() {
        var lease = new WorldContextLease(); Object connection = new Object(), world = new Object();
        lease.bind(connection, world, "token", "minecraft:overworld", 0);
        assertFalse(lease.valid(connection, world, "token", 11_000_000_000L));
        assertTrue(lease.belongsTo(connection));
        assertFalse(lease.belongsTo(new Object()));
    }
}
