package dev.jdguo.land.ui;

import dev.jdguo.land.claim.ClaimKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MapSelectionPanelTest {
    @Test void prefersTopRightAndStaysInsideSmallScreens() {
        for (int[] size : new int[][] {{1920,1080},{854,480},{320,240},{240,180},{168,148}}) {
            var r = MapSelectionPanel.place(size[0], size[1], List.of());
            assertNotNull(r); assertEquals(size[0] - 160, r.x()); assertEquals(8, r.y());
            assertTrue(r.x() >= 8 && r.y() >= 8 && r.x()+r.width() <= size[0]-8 && r.y()+r.height() <= size[1]-8);
        }
    }
    @Test void avoidsXaeroControlsAndFallsBackRatherThanOverlapping() {
        var button = new MapSelectionPanel.Rect(160,0,160,32);
        var r = MapSelectionPanel.place(320,240,List.of(button));
        assertNotNull(r); assertFalse(r.overlaps(button));
        assertNull(MapSelectionPanel.place(320,240,List.of(new MapSelectionPanel.Rect(0,0,320,240))));
        assertNull(MapSelectionPanel.place(150,100,List.of()));
    }
    @Test void statusPanelCapturesClicksIncludingTextAndGaps() {
        var r = new MapSelectionPanel.Rect(8,8,152,108);
        assertTrue(r.contains(60,80)); assertTrue(r.contains(159,115));
        assertFalse(r.contains(160,80)); assertFalse(r.contains(8,116));
    }
    @Test void panelConsumesRightMiddleAndDisabledControlsButKeepsButtonLeftClick() {
        assertFalse(MapSelectionPanel.consumePanelClick(0,true));
        for (int button : new int[] {1,2,3}) assertTrue(MapSelectionPanel.consumePanelClick(button,true));
        for (int button : new int[] {0,1,2}) assertTrue(MapSelectionPanel.consumePanelClick(button,false));
    }
    @Test void lengthyWrappedDiagnosticsFitScreenHeight() {
        var lines = java.util.stream.IntStream.range(0,100).mapToObj(Integer::toString).toList();
        var fitted = MapSelectionPanel.limitTooltipLines(lines,124,10,"…");
        assertEquals(10,fitted.size()); assertEquals("…",fitted.get(9));
        assertEquals(List.of("short"),MapSelectionPanel.limitTooltipLines(List.of("short"),240,10,"…"));
    }
    @Test void reportsFarAwaySelectionAndEmptyState() {
        assertTrue(MapSelectionPanel.selectionLocation(List.of()).contains("开启选地"));
        String value = MapSelectionPanel.selectionLocation(List.of(new ClaimKey("minecraft:overworld",1106,-1018)));
        assertEquals("选区 X 17696～17711 / Z -16288～-16273", value);
    }
    @Test void largeCoordinatesDoNotOverflow() {
        String value = MapSelectionPanel.selectionLocation(List.of(new ClaimKey("minecraft:overworld",Integer.MAX_VALUE,Integer.MIN_VALUE)));
        assertTrue(value.contains("34359738352")); assertTrue(value.contains("-34359738368"));
    }
}
