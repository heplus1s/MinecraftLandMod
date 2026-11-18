package dev.jdguo.land.client;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OwnerColorPagesTest {
    @Test void missingFirstPageNeverBecomesUsableAndRetryCommitsAtomically(){
        var p=new OwnerColorPages();var transfer=UUID.randomUUID();var owner=UUID.randomUUID();
        assertFalse(p.accept(true,transfer,1,2,Map.of(owner,123)));assertFalse(p.ready());
        assertFalse(p.accept(false,UUID.randomUUID(),0,1,Map.of(owner,456)));assertFalse(p.ready());
        var retry=UUID.randomUUID();assertFalse(p.accept(true,retry,0,2,Map.of(owner,123)));assertTrue(p.colors().isEmpty());
        assertTrue(p.accept(true,retry,1,2,Map.of(UUID.randomUUID(),456)));assertTrue(p.ready());assertEquals(123,p.colors().get(owner));
    }
    @Test void resetsAndNewTransfersCannotMixSnapshots(){
        var p=new OwnerColorPages();var old=UUID.randomUUID();var current=UUID.randomUUID();var a=UUID.randomUUID();var b=UUID.randomUUID();
        p.accept(true,old,0,2,Map.of(a,1));p.reset();
        assertFalse(p.accept(true,old,1,2,Map.of(a,2)));
        p.accept(true,current,0,2,Map.of(b,3));assertFalse(p.accept(true,old,1,2,Map.of(a,2)));
        assertTrue(p.accept(true,current,1,2,Map.of(b,4)));assertFalse(p.colors().containsKey(a));assertEquals(4,p.colors().get(b));
    }
    @Test void malformedPagesRejectedAndDeltaRequiresCompleteBaseline(){
        var p=new OwnerColorPages();var id=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->p.accept(true,id,0,0,Map.of()));
        assertThrows(IllegalArgumentException.class,()->p.accept(false,id,1,2,Map.of()));
        assertTrue(p.accept(true,id,0,1,Map.of()));assertTrue(p.ready());
    }
    @Test void partialListingAndSeparateOrdersHaveBoundaries(){
        var parcel=UUID.randomUUID();var sale=UUID.randomUUID();
        assertFalse(ClaimColorBoundary.isBoundary(parcel,null,parcel,null));
        assertTrue(ClaimColorBoundary.isBoundary(parcel,sale,parcel,null));
        assertTrue(ClaimColorBoundary.isBoundary(parcel,sale,parcel,UUID.randomUUID()));
        assertFalse(ClaimColorBoundary.isBoundary(parcel,sale,parcel,sale));
    }
}
