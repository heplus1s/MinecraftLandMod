package dev.jdguo.land.color;

import java.util.Locale;
import java.util.UUID;

/** Shared, deterministic owner colors. Values are RGB, never Xaero byte-order colors. */
public final class OwnerColorPalette {
    private OwnerColorPalette() { }
    public static boolean validRgb(int rgb) { return rgb >= 0 && rgb <= 0xFFFFFF; }
    public static int color(UUID owner) { return automaticRgb(owner); }
    public static int automaticRgb(UUID owner) {
        if (owner == null) return 0x9CA3AF;
        long n = owner.getMostSignificantBits() ^ Long.rotateLeft(owner.getLeastSignificantBits(), 29);
        n = (n ^ (n >>> 30)) * 0xbf58476d1ce4e5b9L;
        n = (n ^ (n >>> 27)) * 0x94d049bb133111ebL;
        n ^= n >>> 31;
        // Fixed chroma/brightness gives readable map colors without java.desktop dependencies.
        int hue = (int) Long.remainderUnsigned(n, 1536);
        return hueRgb(hue);
    }
    public static int hueRgb(int hue) {
        hue = Math.floorMod(hue, 1536);
        int sector = hue / 256, t = hue % 256;
        int low = 70, high = 220, up = low + (high - low) * t / 255, down = high - (high - low) * t / 255;
        return switch (sector) {
            case 0 -> high << 16 | up << 8 | low;
            case 1 -> down << 16 | high << 8 | low;
            case 2 -> low << 16 | high << 8 | up;
            case 3 -> low << 16 | down << 8 | high;
            case 4 -> up << 16 | low << 8 | high;
            default -> high << 16 | low << 8 | down;
        };
    }
    public static int parseHex(String value) {
        if (value == null || !value.matches("#?[0-9a-fA-F]{6}")) throw new IllegalArgumentException("RGB must be six hexadecimal digits");
        return Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16);
    }
    public static String hex(int rgb) {
        if (!validRgb(rgb)) throw new IllegalArgumentException("RGB range");
        return String.format(Locale.ROOT, "#%06X", rgb);
    }
    public static int toXaero(int argb) { return Integer.reverseBytes(argb); }
    public static int xaeroArgb(int rgb, int alpha) {
        if (!validRgb(rgb) || alpha < 0 || alpha > 255) throw new IllegalArgumentException("color range");
        return toXaero(alpha << 24 | rgb);
    }
}
