package dev.jdguo.land.ui.xaero;

import dev.jdguo.land.LandMod;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.world.MapWorld;

public final class WorldSelectionRefresh {
    private static boolean complained;

    private WorldSelectionRefresh() {
    }

    public static void clear() {
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null || !session.isUsable()) {
                return;
            }
            MapProcessor processor = session.getMapProcessor();
            if (processor == null || !processor.isMapWorldUsable()) {
                return;
            }
            MapWorld mapWorld = processor.getMapWorld();
            if (mapWorld != null) {
                mapWorld.clearAllCachedHighlightHashes();
            }
        } catch (Throwable throwable) {
            if (!complained) {
                complained = true;
                LandMod.LOGGER.warn("Could not refresh the land selection on Xaero's world map", throwable);
            }
        }
    }
}
