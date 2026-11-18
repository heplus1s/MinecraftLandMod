package dev.jdguo.land.ui;

import dev.jdguo.land.claim.ClaimKey;
import java.util.List;

/** Pure layout/status logic shared by the map overlay and its small-screen regression tests. */
public final class MapSelectionPanel {
    private MapSelectionPanel() {}
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
        public boolean overlaps(Rect other) {
            return x < other.x + other.width && x + width > other.x && y < other.y + other.height && y + height > other.y;
        }
    }
    public static boolean consumePanelClick(int button, boolean enabledButton) {
        return button != 0 || !enabledButton;
    }
    public static <T> List<T> limitTooltipLines(List<T> lines, int screenHeight, int lineHeight, T ellipsis) {
        int capacity = Math.max(1, (screenHeight - 24) / Math.max(1, lineHeight));
        if (lines.size() <= capacity) return List.copyOf(lines);
        var result = new java.util.ArrayList<T>(lines.subList(0, capacity - 1));
        result.add(ellipsis); return List.copyOf(result);
    }
    public static Rect place(int width, int height, List<Rect> occupied) {
        if (width < 168 || height < 148) return null;
        int[] xs = { width - 160, Math.max(8, Math.min(width - 160, width / 2 - 24)), 8, (width - 152) / 2 };
        for (int start : new int[] {8, 32}) for (int x : xs) for (int y = start; y + 132 <= height - 8; y += 12) {
            Rect panel = new Rect(x, y, 152, 132);
            if (occupied.stream().noneMatch(panel::overlaps)) return panel;
        }
        return null;
    }
    public static String selectionLocation(List<ClaimKey> selected) {
        if (selected.isEmpty()) return "开启选地后点击地图选择";
        long minX = Long.MAX_VALUE, minZ = Long.MAX_VALUE, maxX = Long.MIN_VALUE, maxZ = Long.MIN_VALUE;
        for (ClaimKey key : selected) {
            minX = Math.min(minX, (long) key.chunkX() * 16); minZ = Math.min(minZ, (long) key.chunkZ() * 16);
            maxX = Math.max(maxX, (long) key.chunkX() * 16 + 15); maxZ = Math.max(maxZ, (long) key.chunkZ() * 16 + 15);
        }
        return "选区 X " + minX + "～" + maxX + " / Z " + minZ + "～" + maxZ;
    }
}
