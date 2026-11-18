package dev.jdguo.land.ui;

import java.util.Collection;

/** A login dimension list may be stale after a server/proxy changes the current world.
 * This is only a client preflight: all IDs still go unchanged through server validation. */
public final class LandDimensionPolicy {
    private LandDimensionPolicy() {}
    public static boolean mayRequest(String requested, String current, Collection<String> advertised) {
        return requested != null && !requested.isBlank()
                && (requested.equals(current) || advertised.contains(requested));
    }
}
