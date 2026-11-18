package dev.jdguo.land.network;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class OwnerColorRequestTest {
 private final UUID session=UUID.fromString("00000000-1111-2222-3333-444444444444");
 private PacketByteBuf packet(int op) { var b=new PacketByteBuf(Unpooled.buffer());b.writeVarInt(op);return b; }
 @Test void acceptsBoundedSelfOnlyOperationsWithSession() {
  for(int op:new int[]{0,2}){var b=packet(op);try{b.writeUuid(session);var request=OwnerColorRequest.read(b);assertEquals(op,request.operation());assertEquals(session,request.session());}finally{b.release();}}
  var b=packet(1);try{b.writeInt(0x123456);b.writeUuid(session);assertEquals(0x123456,OwnerColorRequest.read(b).rgb());}finally{b.release();}
 }
 @Test void rejectsInvalidRangeOperationTruncationAndForgedOwner() {
  for(int rgb:new int[]{-1,0x1000000}){var b=packet(1);try{b.writeInt(rgb);b.writeUuid(session);assertThrows(RuntimeException.class,()->OwnerColorRequest.read(b));}finally{b.release();}}
  for(int op:new int[]{-1,3}){var b=packet(op);try{b.writeUuid(session);assertThrows(RuntimeException.class,()->OwnerColorRequest.read(b));}finally{b.release();}}
  for(int op:new int[]{0,1,2}){var b=packet(op);try{if(op==1)b.writeInt(0xabcdef);b.writeUuid(session);b.writeUuid(UUID.randomUUID());assertThrows(RuntimeException.class,()->OwnerColorRequest.read(b));}finally{b.release();}}
 }
 @Test void oldPacketsWithoutSessionOrTruncatedUuidAreRejected() {
  for(int op:new int[]{0,1,2}){var b=packet(op);try{if(op==1)b.writeInt(0x123456);assertThrows(RuntimeException.class,()->OwnerColorRequest.read(b));}finally{b.release();}}
  var b=packet(0);try{b.writeLong(42);assertThrows(RuntimeException.class,()->OwnerColorRequest.read(b));}finally{b.release();}
 }
 @Test void rejectsStaleConnectionIdentity() {
  Object p=new Object(),h=new Object();assertTrue(RequestAccess.current(p,p,h,h));assertFalse(RequestAccess.current(p,new Object(),h,h));assertFalse(RequestAccess.current(p,p,h,new Object()));
 }
}
