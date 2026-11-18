package dev.jdguo.land.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LandPickDimensionTest {
    @Test void gridDiscardsForeignMapSelectionWithoutMovingIt() {
        LandPick.clear();
        LandPick.click("minecraft:ncepu_overworld", 1, 2);
        int oldGeneration = LandPick.generation();
        LandPick.retainDimension("minecraft:overworld");
        assertTrue(LandPick.selected().isEmpty());
        assertTrue(LandPick.generation() > oldGeneration);
    }
    @Test void sessionBoundSelectionCannotSilentlyBecomeLegacy() {
        LandPick.clear(); LandPick.click("minecraft:overworld", 1, 2); LandPick.requireContext();
        LandPick.retainDimension("minecraft:overworld"); assertTrue(LandPick.requiresContext());
        LandPick.click("minecraft:overworld", 3, 4); assertTrue(LandPick.requiresContext());
        LandPick.clear(); assertFalse(LandPick.requiresContext());
    }
    @Test void sameWorldSelectionIsPreservedButNewWorldCannotMix() {
        LandPick.clear();
        LandPick.click("minecraft:overworld", 1, 2);
        LandPick.retainDimension("minecraft:overworld");
        assertEquals(1, LandPick.selected().size());
        LandPick.click("minecraft:the_nether", 3, 4);
        assertEquals(1, LandPick.selected().size());
        assertTrue(LandPick.contains("minecraft:the_nether", 3, 4));
        assertFalse(LandPick.contains("minecraft:overworld", 1, 2));
        LandPick.clear();
    }
}
