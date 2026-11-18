package dev.jdguo.land.network;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class OwnerColorSessionTest {
 @Test void refusesOldWorldHandlerAndClientEpoch(){
  Object h=new Object(),w=new Object();UUID epoch=UUID.randomUUID();var s=new OwnerColorSession(h,w,epoch);
  assertTrue(s.matches(h,w,epoch));assertFalse(s.matches(new Object(),w,epoch));assertFalse(s.matches(h,new Object(),epoch));assertFalse(s.matches(h,w,UUID.randomUUID()));assertFalse(s.matches(null,w,epoch));assertFalse(s.matches(h,w,null));
 }
}
