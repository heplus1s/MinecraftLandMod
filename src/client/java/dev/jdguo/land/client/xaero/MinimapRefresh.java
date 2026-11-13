package dev.jdguo.land.client.xaero;

import dev.jdguo.land.LandMod;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.MinimapProcessor;
import xaero.common.minimap.highlight.DimensionHighlighterHandler;
import xaero.common.minimap.write.MinimapWriter;

final class MinimapRefresh {
    private static boolean complained;

    private MinimapRefresh() {
    }

    static boolean clear() {
        try {
            XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
            if (session == null) {
                return false;
            }
            MinimapProcessor processor = session.getMinimapProcessor();
            if (processor == null) {
                return false;
            }
            MinimapWriter writer = processor.getMinimapWriter();
            if (writer == null) {
                return false;
            }
            DimensionHighlighterHandler handler = writer.getDimensionHighlightHandler();
            if (handler == null) {
                return false;
            }
            handler.requestRefresh();
            return true;
        } catch (Throwable throwable) {
            if (!complained) {
                complained = true;
                LandMod.LOGGER.warn("Could not refresh Xaero minimap claims", throwable);
            }
            return true;
        }
    }
}
