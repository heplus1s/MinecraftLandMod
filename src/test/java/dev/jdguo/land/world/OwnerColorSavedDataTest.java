package dev.jdguo.land.world;
import dev.jdguo.land.color.OwnerColorPalette;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class OwnerColorSavedDataTest {
 @Test void nbtRoundTripAndResetPreserveOtherOwner() {
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();var data=new OwnerColorSavedData();
  assertTrue(data.set(a,0x102030));assertTrue(data.set(b,0xaabbcc));assertFalse(data.set(a,0x102030));
  var loaded=OwnerColorSavedData.fromNbt(data.writeNbt(new NbtCompound()));
  assertEquals(0x102030,loaded.color(a));assertEquals(0xaabbcc,loaded.color(b));
  assertTrue(loaded.reset(a));assertEquals(OwnerColorPalette.color(a),loaded.color(a));assertEquals(0xaabbcc,loaded.color(b));
  assertFalse(loaded.reset(a));assertThrows(UnsupportedOperationException.class,()->loaded.overrides().clear());
 }
 @Test void neighboringAutomaticAssignmentSurvivesReloadResetAndNewNeighbors() {
  UUID a=new UUID(0,1),b=new UUID(0,2);var data=new OwnerColorSavedData();
  data.set(a,OwnerColorPalette.color(b));
  var cells=java.util.List.of(new dev.jdguo.land.color.OwnerColorAllocator.Cell("world",0,0,a),new dev.jdguo.land.color.OwnerColorAllocator.Cell("world",1,0,b));
  data.ensure(cells);int allocated=data.color(b);assertNotEquals(OwnerColorPalette.color(b),allocated);
  data.set(b,0x123456);var restored=OwnerColorSavedData.fromNbt(data.writeNbt(new NbtCompound()));
  assertEquals(0x123456,restored.color(b));restored.reset(b);assertEquals(allocated,restored.color(b));
  restored.ensure(java.util.List.of(new dev.jdguo.land.color.OwnerColorAllocator.Cell("world",100,0,b)));
  assertEquals(allocated,restored.color(b));assertEquals(allocated,restored.effective().get(b));
 }
 @Test void malformedNbtEntriesAreIgnored() {
  UUID good=UUID.randomUUID(),bad=UUID.randomUUID();var entries=new NbtCompound();entries.putInt(good.toString(),123);entries.putInt(bad.toString(),-1);entries.putInt("not-a-uuid",42);
  var n=new NbtCompound();n.put("Overrides",entries);var data=OwnerColorSavedData.fromNbt(n);assertEquals(1,data.overrides().size());assertEquals(123,data.color(good));
 }
 @Test void rejectsInvalidMutation() {
  var d=new OwnerColorSavedData();assertThrows(IllegalArgumentException.class,()->d.set(UUID.randomUUID(),-1));assertThrows(IllegalArgumentException.class,()->d.set(null,0));
 }
}
