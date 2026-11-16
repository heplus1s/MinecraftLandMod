package dev.jdguo.land.ui;

import net.fabricmc.loader.api.FabricLoader;

final class MapRefresh {
    private MapRefresh() {
    }

    static void mark() {
        if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) {
            dev.jdguo.land.ui.xaero.WorldSelectionRefresh.clear();
        }
        if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
            dev.jdguo.land.ui.xaero.MiniSelectionRefresh.clear();
        }
    }
}
