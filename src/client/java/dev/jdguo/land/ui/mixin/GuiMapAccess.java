package dev.jdguo.land.ui.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public interface GuiMapAccess {
    @Accessor("mouseBlockPosX")
    int land$blockX();

    @Accessor("mouseBlockPosZ")
    int land$blockZ();

    @Accessor("mouseBlockDim")
    RegistryKey<World> land$dimension();
}
