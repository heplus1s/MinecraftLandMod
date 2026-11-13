package dev.jdguo.land.client.xaero;

import dev.jdguo.land.LandMod;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.world.MapWorld;

final class WorldMapRefresh {
    private static boolean complained;

    private WorldMapRefresh() {
    }

    static boolean clear() {
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null || !session.isUsable()) {
                return false;
            }
            MapProcessor processor = session.getMapProcessor();
            if (processor == null || !processor.isMapWorldUsable()) {
                return false;
            }
            MapWorld mapWorld = processor.getMapWorld();
            if (mapWorld == null) {
                return false;
            }
            mapWorld.clearAllCachedHighlightHashes();
            return true;
        } catch (Throwable throwable) {
            if (!complained) {
                complained = true;
                LandMod.LOGGER.warn("Could not refresh Xaero world map claims", throwable);
            }
            return true;
        }
    }
}
