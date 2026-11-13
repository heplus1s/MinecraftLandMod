package dev.jdguo.land.protect;

import dev.jdguo.land.claim.ClaimMap;
import dev.jdguo.land.message.LandMessages;
import dev.jdguo.land.world.ClaimSavedData;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClaimProtection {
    private static final long MESSAGE_GAP_TICKS = 40;
    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    private ClaimProtection() {
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return true;
            }
            if (allows(serverPlayer, pos)) {
                return true;
            }
            tell(serverPlayer);
            return false;
        });

        UseBlockCallback.EVENT.register(ClaimProtection::onUseBlock);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            if (!ContainerInteraction.isInventoryEntity(entity)) {
                return ActionResult.PASS;
            }
            if (allows(serverPlayer, entity.getBlockPos())) {
                return ActionResult.PASS;
            }
            tell(serverPlayer);
            return ActionResult.FAIL;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_MESSAGE.remove(handler.player.getUuid()));
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        BlockPos clicked = hit.getBlockPos();
        var state = world.getBlockState(clicked);
        ItemStack stack = player.getStackInHand(hand);
        boolean placing = stack.getItem() instanceof BlockItem;
        boolean container = ContainerInteraction.isContainer(world, clicked, state);
        if (container && !(player.isSneaking() && placing)) {
            if (!allows(serverPlayer, clicked)) {
                tell(serverPlayer);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        }
        if (placing) {
            BlockPos placeAt = state.isReplaceable() ? clicked : clicked.offset(hit.getSide());
            if (!allows(serverPlayer, placeAt)) {
                tell(serverPlayer);
                return ActionResult.FAIL;
            }
        }
        return ActionResult.PASS;
    }

    public static boolean allows(ServerPlayerEntity player, BlockPos pos) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return true;
        }
        ClaimMap claims = ClaimSavedData.get(world.getServer()).claims();
        String dimension = world.getRegistryKey().getValue().toString();
        return claims.allowsChange(dimension, pos.getX(), pos.getZ(), player.getUuid(), player.hasPermissionLevel(2));
    }

    public static void filterExplosion(ServerWorld world, @Nullable LivingEntity cause, List<BlockPos> blocks) {
        UUID actor = null;
        boolean operator = false;
        if (cause instanceof PlayerEntity player) {
            actor = player.getUuid();
            operator = player.hasPermissionLevel(2);
        }
        ClaimMap claims = ClaimSavedData.get(world.getServer()).claims();
        String dimension = world.getRegistryKey().getValue().toString();
        UUID actorId = actor;
        boolean op = operator;
        blocks.removeIf(pos -> !claims.allowsChange(dimension, pos.getX(), pos.getZ(), actorId, op));
    }

    private static void tell(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        long now = world.getTime();
        Long last = LAST_MESSAGE.get(player.getUuid());
        if (last != null && now - last < MESSAGE_GAP_TICKS) {
            return;
        }
        LAST_MESSAGE.put(player.getUuid(), now);
        player.sendMessage(LandMessages.chat("你不能在这里这样做。"), false);
    }
}
