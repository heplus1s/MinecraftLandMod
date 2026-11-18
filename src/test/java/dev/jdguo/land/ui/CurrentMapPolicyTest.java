package dev.jdguo.land.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CurrentMapPolicyTest {
    @Test void onlyCurrentStableAutomaticPlayerMapCanBind() {
        assertTrue(CurrentMapPolicy.mayBind(true,true,true,true,true,true));
        for(int i=0;i<6;i++) {
            boolean[] conditions={true,true,true,true,true,true}; conditions[i]=false;
            assertFalse(CurrentMapPolicy.mayBind(conditions[0],conditions[1],conditions[2],conditions[3],conditions[4],conditions[5]));
        }
    }
}
