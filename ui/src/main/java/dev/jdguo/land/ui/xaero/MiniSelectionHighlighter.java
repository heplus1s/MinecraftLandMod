package dev.jdguo.land.ui.xaero;

import dev.jdguo.land.ui.LandPick;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.common.minimap.info.render.compile.InfoDisplayCompiler;

public final class MiniSelectionHighlighter extends ChunkHighlighter {
    public MiniSelectionHighlighter() {
        super(true);
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
    public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, RegistryKey<World> dimension, int chunkX, int chunkZ, int width) {
        if (LandPick.contains(dimension.getValue().toString(), chunkX, chunkZ)) {
            compiler.addLine(Text.literal("已选"));
        }
    }
}
