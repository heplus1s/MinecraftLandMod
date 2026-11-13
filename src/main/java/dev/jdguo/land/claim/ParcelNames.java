package dev.jdguo.land.claim;

/**
 * Display names players give a parcel. Empty means unnamed.
 * The limit is counted in Unicode code points so a Chinese name is not cut in bytes.
 */
public final class ParcelNames {
    public static final int MAX_LENGTH = 32;

    private ParcelNames() {
    }

    /**
     * @return the trimmed name, an empty string when the name is cleared, or null when it is illegal
     */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.trim();
        if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
            return null;
        }
        for (int index = 0; index < name.length(); ) {
            int codePoint = name.codePointAt(index);
            if (Character.isISOControl(codePoint)) {
                return null;
            }
            index += Character.charCount(codePoint);
        }
        return name;
    }
}
