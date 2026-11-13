package dev.jdguo.land.ui.xaero;

import dev.jdguo.land.ui.LandPick;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;

public final class WorldSelectionHighlighter extends ChunkHighlighter {
    public WorldSelectionHighlighter() {
        super(true);
    }

    @Override
    public int calculateRegionHash(RegistryKey<World> dimension, int regionX, int regionZ) {
        int hash = 17;
        hash = 31 * hash + LandPick.generation();
        hash = 31 * hash + regionX;
        hash = 31 * hash + regionZ;
        String id = dimension.getValue().toString();
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                if (LandPick.contains(id, (regionX << 5) + x, (regionZ << 5) + z)) {
                    hash = 31 * hash + x;
                    hash = 31 * hash + z;
                }
            }
        }
        return hash;
    }

    @Override
    public boolean regionHasHighlights(RegistryKey<World> dimension, int regionX, int regionZ) {
        String id = dimension.getValue().toString();
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                if (LandPick.contains(id, (regionX << 5) + x, (regionZ << 5) + z)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean chunkIsHighlit(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return LandPick.contains(dimension.getValue().toString(), chunkX, chunkZ);
    }

    @Override
    protected int[] getColors(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        if (!chunkIsHighlit(dimension, chunkX, chunkZ)) {
            return null;
        }
        int fill = 0x664C8DFF;
        int edge = 0xFF4C8DFF;
        String id = dimension.getValue().toString();
        resultStore[0] = fill;
        resultStore[1] = LandPick.contains(id, chunkX, chunkZ - 1) ? fill : edge;
        resultStore[2] = LandPick.contains(id, chunkX + 1, chunkZ) ? fill : edge;
        resultStore[3] = LandPick.contains(id, chunkX, chunkZ + 1) ? fill : edge;
        resultStore[4] = LandPick.contains(id, chunkX - 1, chunkZ) ? fill : edge;
        return resultStore;
    }

    @Override
    public Text getChunkHighlightSubtleTooltip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return tip(dimension, chunkX, chunkZ);
    }

    @Override
    public Text getChunkHighlightBluntTooltip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return tip(dimension, chunkX, chunkZ);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Text> list, RegistryKey<World> dimension, int blockX, int blockZ, int width) {
        Text text = tip(dimension, blockX >> 4, blockZ >> 4);
        if (text != null) {
            list.add(text);
        }
    }

    private static Text tip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        if (!LandPick.contains(dimension.getValue().toString(), chunkX, chunkZ)) {
            return null;
        }
        return Text.literal("已选");
    }
}
