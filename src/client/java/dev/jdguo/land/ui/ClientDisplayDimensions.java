package dev.jdguo.land.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/** Keeps Xaero World Map optional for minimap-only clients. */
public final class ClientDisplayDimensions {
    private ClientDisplayDimensions() {}
    public static String resolve(RegistryKey<World> requested) {
        if (requested == null) return "";
        if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) {
            String resolved = dev.jdguo.land.ui.xaero.CurrentMapDimension.displayDimension(requested);
            return resolved == null ? "" : resolved;
        }
        return requested.getValue().toString();
    }
}
