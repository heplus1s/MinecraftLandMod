package dev.jdguo.land.client.xaero;

import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.common.minimap.info.render.compile.InfoDisplayCompiler;

/**
 * Minimap 23.9.3 highlighter. Registered on
 * {@code xaero.common.minimap.highlight.HighlighterRegistry} just before it is sealed.
 */
public final class MinimapClaimHighlighter extends ChunkHighlighter {
    public MinimapClaimHighlighter() {
        super(true);
    }

    @Override
    public boolean regionHasHighlights(RegistryKey<World> dimension, int regionX, int regionZ) {
        return ClaimHighlighterLogic.regionHas(dimension, regionX, regionZ);
    }

    @Override
    public boolean chunkIsHighlit(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return ClaimHighlighterLogic.chunkClaimed(dimension, chunkX, chunkZ);
    }

    @Override
    protected int[] getColors(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        if (!ClaimHighlighterLogic.fill(resultStore, dimension, chunkX, chunkZ)) {
            return null;
        }
        return resultStore;
    }

    @Override
    public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, RegistryKey<World> dimension, int chunkX, int chunkZ, int width) {
        Text text = ClaimHighlighterLogic.tooltip(dimension, chunkX, chunkZ);
        if (text != null) {
            compiler.addLine(text);
        }
    }
}
