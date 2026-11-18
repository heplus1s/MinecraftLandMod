package dev.jdguo.land.ui.xaero;

import dev.jdguo.land.ui.ClientWorldContext;
import dev.jdguo.land.ui.CurrentMapPolicy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;

/** Adapts the live automatically selected map only, never an archived/manual map. */
public final class CurrentMapDimension {
    private CurrentMapDimension() {}
    public record View(Object processor, Object world, Object map, Object dimension, Object future,
                       String currentMap, String selectedMap, boolean automatic) {}
    public static View view(MapProcessor processor) {
        try {
            if (processor == null || !processor.isMapWorldUsable()) return null;
            var map = processor.getMapWorld();
            var dimension = map == null ? null : map.getCurrentDimension();
            if (dimension == null) return null;
            var selected = dimension.getSelectedMapKeyUnsynced();
            return new View(processor, processor.getWorld(), map, dimension, map.getFutureDimension(),
                    dimension.getCurrentMultiworld(), selected == null ? null : selected.toString(), dimension.isAutoSelected());
        } catch (RuntimeException | LinkageError unavailable) { return null; }
    }
    public static String resolve(RegistryKey<World> requested, MapProcessor processor) {
        if (requested == null) return null;
        var client = MinecraftClient.getInstance();
        try {
            if (processor != null && client.world != null) {
                var map = processor.getMapWorld();
                var viewed = map == null ? null : map.getCurrentDimension();
                if (viewed != null) {
                    var playerMap = map.getPlayerMapKey();
                    var selectedMap = viewed.getSelectedMapKeyUnsynced();
                    boolean currentAuto = CurrentMapPolicy.mayBind(
                            processor.getWorld() == client.world && processor.mainWorld == client.world,
                            processor.isMapWorldUsable() && !processor.isWaitingForWorldUpdate(),
                            viewed == map.getFutureDimension(), requested.equals(viewed.getDimId()),
                            viewed.isAutoSelected(), playerMap != null && playerMap.equals(selectedMap));
                    if (currentAuto) return ClientWorldContext.currentDimension(client);
                }
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // Fail closed on unsupported Xaero versions. Do not infer an alias from its name.
            return null;
        }
        // No exact-ID fallback: an archived/manual map may reuse a real dimension ID.
        // Only the verified current automatic player map can produce a trade selection.
        return null;
    }
    public static String displayDimension(RegistryKey<World> requested) {
        try {
            var session = WorldMapSession.getCurrentSession();
            return resolve(requested, session == null || !session.isUsable() ? null : session.getMapProcessor());
        } catch (RuntimeException | LinkageError unavailable) { return null; }
    }
}
