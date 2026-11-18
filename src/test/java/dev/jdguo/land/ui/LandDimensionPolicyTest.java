package dev.jdguo.land.ui;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LandDimensionPolicyTest {
    @Test void dynamicCurrentWorldDoesNotRequireLoginListEntry() {
        assertTrue(LandDimensionPolicy.mayRequest("minecraft:ncepu_overworld", "minecraft:ncepu_overworld", List.of("minecraft:overworld")));
    }
    @Test void knownRemoteWorldRemainsSelectable() {
        assertTrue(LandDimensionPolicy.mayRequest("minecraft:the_nether", "minecraft:overworld", List.of("minecraft:the_nether")));
    }
    @Test void unknownMapAliasIsNeverRemapped() {
        assertFalse(LandDimensionPolicy.mayRequest("minecraft:ncepu_overworld", "minecraft:overworld", List.of("minecraft:overworld")));
        assertFalse(LandDimensionPolicy.mayRequest("", "", List.of()));
        assertFalse(LandDimensionPolicy.mayRequest(null, "minecraft:overworld", List.of()));
    }
}
