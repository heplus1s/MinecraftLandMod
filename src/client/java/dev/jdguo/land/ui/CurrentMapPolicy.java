package dev.jdguo.land.ui;

/** Every condition is required; map display names/suffixes are never evidence. */
public final class CurrentMapPolicy {
    private CurrentMapPolicy() {}
    public static boolean mayBind(boolean sameClientWorld, boolean mapReady, boolean stableView,
                                  boolean clickedViewedDimension, boolean autoSelected, boolean samePlayerMap) {
        return sameClientWorld && mapReady && stableView && clickedViewedDimension && autoSelected && samePlayerMap;
    }
}
