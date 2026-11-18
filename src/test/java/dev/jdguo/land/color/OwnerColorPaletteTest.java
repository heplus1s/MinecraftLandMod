package dev.jdguo.land.color;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OwnerColorPaletteTest {
 @Test void stableUuidColorsAndBounds() {
  UUID u=UUID.fromString("93b76a1f-2147-4c9d-ade2-1234567890ab");
  assertEquals(OwnerColorPalette.color(u),OwnerColorPalette.color(UUID.fromString(u.toString())));
  Set<Integer> colors=new HashSet<>();for(int i=0;i<1000;i++){int c=OwnerColorPalette.color(new UUID(i,i*31L));assertTrue(OwnerColorPalette.validRgb(c));colors.add(c);}assertTrue(colors.size()>100);
 }
 @Test void hexAndXaeroByteOrder() {
  assertEquals(0x1234af,OwnerColorPalette.parseHex("#1234aF"));assertEquals("#1234AF",OwnerColorPalette.hex(0x1234af));
  assertEquals(0x5634127f,OwnerColorPalette.xaeroArgb(0x123456,0x7f));assertEquals(0x5634127f,OwnerColorPalette.toXaero(0x7f123456));
  for(String value:List.of("", "123", "#1234567", " #123456", "GGGGGG", "-00001"))assertThrows(IllegalArgumentException.class,()->OwnerColorPalette.parseHex(value));
 }
}
