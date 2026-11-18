package dev.jdguo.land.color;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OwnerColorAllocatorTest {
 private final UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3);
 private OwnerColorAllocator.Cell cell(String dim,int x,int z,UUID owner){return new OwnerColorAllocator.Cell(dim,x,z,owner);}
 @Test void adjacencyImprovesDistanceAndDoesNotRecolorAssigned() {
  int base=OwnerColorPalette.color(b);
  var claims=List.of(cell("overworld",0,0,a),cell("overworld",1,0,b));
  var assigned=Map.of(a,base);var result=OwnerColorAllocator.allocate(claims,assigned,Map.of());
  assertFalse(result.containsKey(a));assertTrue(OwnerColorAllocator.distanceSquared(result.get(b),base)>0);
  assertEquals(base,assigned.get(a));
 }
 @Test void stableOrderAndAllParcelsShareOneColor() {
  var claims=new ArrayList<>(List.of(cell("overworld",0,0,a),cell("overworld",1,0,b),cell("overworld",2,0,c),cell("nether",9,9,a)));
  var first=OwnerColorAllocator.allocate(claims,Map.of(),Map.of());Collections.reverse(claims);
  assertEquals(first,OwnerColorAllocator.allocate(claims,Map.of(),Map.of()));assertEquals(3,first.size());
  assertTrue(OwnerColorAllocator.allocate(claims,first,Map.of()).isEmpty());
 }
 @Test void onlyFourNeighborsInSameDimensionAndCustomColorWins() {
  int base=OwnerColorPalette.color(b);
  var separated=List.of(cell("nether",0,0,a),cell("overworld",1,0,b));
  assertEquals(base,OwnerColorAllocator.allocate(separated,Map.of(a,base),Map.of()).get(b));
  var diagonal=List.of(cell("overworld",0,0,a),cell("overworld",1,1,b));
  assertEquals(base,OwnerColorAllocator.allocate(diagonal,Map.of(a,base),Map.of()).get(b));
  var adjacent=List.of(cell("overworld",0,0,a),cell("overworld",1,0,b));
  assertNotEquals(base,OwnerColorAllocator.allocate(adjacent,Map.of(a,0),Map.of(a,base)).get(b));
 }
 @Test void extremeCoordinatesDoNotWrapAndNoNeighborUsesUuid() {
  var claims=List.of(cell("world",Integer.MAX_VALUE,0,a),cell("world",Integer.MIN_VALUE,0,b));
  assertEquals(OwnerColorPalette.color(b),OwnerColorAllocator.allocate(claims,Map.of(a,OwnerColorPalette.color(b)),Map.of()).get(b));
 }
}
