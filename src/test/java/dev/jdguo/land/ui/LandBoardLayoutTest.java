package dev.jdguo.land.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LandBoardLayoutTest {
    @Test void fitsScaledWindowsWithoutCoveringNavigationOrFooter() {
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}}) {
            var layout = LandBoardLayout.fit(size[0], size[1], 13);
            assertTrue(layout.x() - 28 >= 0);
            assertTrue(layout.x() + layout.span() * layout.cell() + 28 <= size[0]);
            assertTrue(layout.y() + layout.span() * layout.cell() + 20 < size[1] - 72);
        }
    }
    @Test void rejectsOutsideEdgesRatherThanTruncatingNegativeFractionToZero() {
        var layout = LandBoardLayout.fit(427, 240, 13);
        assertFalse(layout.contains(layout.x() - 0.1, layout.y()));
        assertFalse(layout.contains(layout.x(), layout.y() - 0.1));
        assertFalse(layout.contains(layout.x() + 13 * layout.cell(), layout.y()));
        assertFalse(layout.contains(layout.x(), layout.y() + 13 * layout.cell()));
        assertTrue(layout.contains(layout.x(), layout.y()));
        assertTrue(layout.contains(layout.x() + 13 * layout.cell() - 0.1, layout.y() + 13 * layout.cell() - 0.1));
    }
}
