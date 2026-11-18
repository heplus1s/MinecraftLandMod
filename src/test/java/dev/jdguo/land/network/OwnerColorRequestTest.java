package dev.jdguo.land.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OwnerColorRequestTest {
    private PacketByteBuf packet(int op) { var b = new PacketByteBuf(Unpooled.buffer()); b.writeVarInt(op); return b; }
    @Test void acceptsBoundedSelfOnlyOperations() {
        for (int op : new int[]{0, 2}) { var b=packet(op); try { assertEquals(op, OwnerColorRequest.read(b).operation()); } finally { b.release(); } }
        var b=packet(1); try { b.writeInt(0x123456); assertEquals(0x123456, OwnerColorRequest.read(b).rgb()); } finally { b.release(); }
    }
    @Test void rejectsInvalidRangeOperationTruncationAndForgedOwner() {
        for (int rgb : new int[]{-1, 0x1000000}) { var b=packet(1); try { b.writeInt(rgb); assertThrows(RuntimeException.class, () -> OwnerColorRequest.read(b)); } finally { b.release(); } }
        for (int op : new int[]{-1,3}) {var b=packet(op);try {assertThrows(RuntimeException.class, () -> OwnerColorRequest.read(b));}finally {b.release();}}
        var truncated=packet(1);try {assertThrows(RuntimeException.class, () -> OwnerColorRequest.read(truncated));}finally{truncated.release();}
        for (int op : new int[]{0,1,2}) {var b=packet(op);try {if(op==1)b.writeInt(0xabcdef);b.writeUuid(UUID.randomUUID());assertThrows(RuntimeException.class, () -> OwnerColorRequest.read(b));}finally{b.release();}}
    }
    @Test void rejectsStaleConnectionIdentity() {
        Object player=new Object(),handler=new Object();
        assertTrue(RequestAccess.current(player,player,handler,handler));
        assertFalse(RequestAccess.current(player,new Object(),handler,handler));
        assertFalse(RequestAccess.current(player,player,handler,new Object()));
    }
}
