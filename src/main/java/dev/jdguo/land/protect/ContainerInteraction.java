package dev.jdguo.land.protect;

import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BeaconBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CartographyTableBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Containers and item-holding blocks a non-member should not open.
 * Doors, buttons, and levers are left alone in this version.
 */
public final class ContainerInteraction {
    private ContainerInteraction() {
    }

    public static boolean isContainer(World world, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof Inventory) {
            return true;
        }
        return state.getBlock() instanceof CraftingTableBlock
                || state.getBlock() instanceof EnderChestBlock
                || state.getBlock() instanceof AnvilBlock
                || state.getBlock() instanceof EnchantingTableBlock
                || state.getBlock() instanceof GrindstoneBlock
                || state.getBlock() instanceof SmithingTableBlock
                || state.getBlock() instanceof CartographyTableBlock
                || state.getBlock() instanceof LoomBlock
                || state.getBlock() instanceof StonecutterBlock
                || state.getBlock() instanceof BeaconBlock
                || state.getBlock() instanceof LecternBlock
                || state.getBlock() instanceof DecoratedPotBlock
                || state.getBlock() instanceof JukeboxBlock;
    }

    public static boolean isInventoryEntity(Entity entity) {
        if (entity instanceof Inventory) {
            return true;
        }
        return entity instanceof AbstractDonkeyEntity donkey && donkey.hasChest();
    }
}
