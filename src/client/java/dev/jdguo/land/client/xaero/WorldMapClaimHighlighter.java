package dev.jdguo.land.client.xaero;

import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;

/**
 * World Map 1.37.2 highlighter. Registered on {@code xaero.map.highlight.HighlighterRegistry}
 * just before that registry is sealed.
 */
public final class WorldMapClaimHighlighter extends ChunkHighlighter {
    public WorldMapClaimHighlighter() {
        super(true);
    }

    @Override
    public int calculateRegionHash(RegistryKey<World> dimension, int regionX, int regionZ) {
        return ClaimHighlighterLogic.regionHash(dimension, regionX, regionZ);
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
    public Text getChunkHighlightSubtleTooltip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return ClaimHighlighterLogic.tooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public Text getChunkHighlightBluntTooltip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return ClaimHighlighterLogic.tooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Text> list, RegistryKey<World> dimension, int blockX, int blockZ, int width) {
        Text text = ClaimHighlighterLogic.tooltip(dimension, blockX >> 4, blockZ >> 4);
        if (text != null) {
            list.add(text);
        }
    }
}
