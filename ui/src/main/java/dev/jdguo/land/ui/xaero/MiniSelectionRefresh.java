package dev.jdguo.land.ui.xaero;

import dev.jdguo.land.LandMod;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.MinimapProcessor;
import xaero.common.minimap.highlight.DimensionHighlighterHandler;
import xaero.common.minimap.write.MinimapWriter;

public final class MiniSelectionRefresh {
    private static boolean complained;

    private MiniSelectionRefresh() {
    }

    public static void clear() {
        try {
            XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
            if (session == null) {
                return;
            }
            MinimapProcessor processor = session.getMinimapProcessor();
            if (processor == null) {
                return;
            }
            MinimapWriter writer = processor.getMinimapWriter();
            if (writer == null) {
                return;
            }
            DimensionHighlighterHandler handler = writer.getDimensionHighlightHandler();
            if (handler != null) {
                handler.requestRefresh();
            }
        } catch (Throwable throwable) {
            if (!complained) {
                complained = true;
                LandMod.LOGGER.warn("Could not refresh the land selection on Xaero's minimap", throwable);
            }
        }
    }
}
