package dev.jdguo.land.claim;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NearbyOwnersTest {
    @Test void sameOwnerOnlyOnceAndSelectionsIndependent() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var rows=List.of(new NearbyOwners.Owned(0,0,a),new NearbyOwners.Owned(1,1,a),new NearbyOwners.Owned(8,0,b));
        assertArrayEquals(new int[]{2,1,0},NearbyOwners.count(rows,new int[]{0,0,10,0,100,100},8));
    }
    @Test void negativeAndExtremeCoordinatesHaveInclusiveBoundaries() {
        UUID a=UUID.randomUUID(); var rows=List.of(new NearbyOwners.Owned(-8,-8,a),new NearbyOwners.Owned(Integer.MAX_VALUE,0,a));
        assertArrayEquals(new int[]{1,0},NearbyOwners.count(rows,new int[]{0,0,Integer.MIN_VALUE,0},8));
    }
    @Test void radiusZeroCountsOnlySelectedChunk() {
        assertArrayEquals(new int[]{1,0},NearbyOwners.count(List.of(new NearbyOwners.Owned(-1,-1,UUID.randomUUID())),new int[]{-1,-1,-1,0},0));
    }
    @Test void randomSpatialIndexMatchesBruteForce() {
        Random r=new Random(42); var rows=new ArrayList<NearbyOwners.Owned>(); var ids=new UUID[]{UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()};
        for(int i=0;i<500;i++)rows.add(new NearbyOwners.Owned(r.nextInt(300)-150,r.nextInt(300)-150,ids[r.nextInt(3)]));
        for(int radius:new int[]{0,1,8,128}) for(int i=0;i<100;i++) {
            int x=r.nextInt(500)-250,z=r.nextInt(500)-250; Set<UUID> expected=new HashSet<>();
            for(var c:rows)if(Math.abs((long)x-c.x())<=radius&&Math.abs((long)z-c.z())<=radius)expected.add(c.owner());
            assertEquals(expected.size(),NearbyOwners.count(rows,new int[]{x,z},radius)[0]);
        }
    }
}
