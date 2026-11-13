package dev.jdguo.land.client.xaero;

import net.fabricmc.loader.api.FabricLoader;

public final class XaeroRefresh {
    private static final boolean WORLD_MAP = FabricLoader.getInstance().isModLoaded("xaeroworldmap");
    private static final boolean MINIMAP = FabricLoader.getInstance().isModLoaded("xaerominimap");
    private static boolean pending;

    private XaeroRefresh() {
    }

    public static void mark() {
        if (!WORLD_MAP && !MINIMAP) {
            return;
        }
        pending = true;
    }

    public static void tick() {
        if (!pending) {
            return;
        }
        boolean worldReady = !WORLD_MAP || WorldMapRefresh.clear();
        boolean minimapReady = !MINIMAP || MinimapRefresh.clear();
        if (worldReady && minimapReady) {
            pending = false;
        }
    }
}
