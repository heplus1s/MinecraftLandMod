package dev.jdguo.land.world;

import dev.jdguo.land.claim.Claim;
import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.ClaimMap;
import dev.jdguo.land.network.ClaimSync;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * All dimensions share one file on the overworld: {@code data/landmod_claims.dat}.
 * A later version should keep reading format 1. Unknown keys are written back.
 */
public final class ClaimSavedData extends PersistentState {
    public static final String ID = "landmod_claims";

    private final MinecraftServer server;
    private final ClaimMap claims;

    private ClaimSavedData(MinecraftServer server, ClaimMap claims) {
        this.server = server;
        this.claims = claims;
    }

    public static ClaimSavedData get(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        return overworld.getPersistentStateManager().getOrCreate(
                nbt -> new ClaimSavedData(server, ClaimMap.fromNbt(nbt)),
                () -> new ClaimSavedData(server, new ClaimMap()),
                ID);
    }

    public ClaimMap claims() {
        return claims;
    }

    public ClaimMap.Result claim(ServerPlayerEntity player, int maxClaims) {
        ClaimKey key = standing(player);
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.claim(key.dimension(), key.chunkX(), key.chunkZ(),
                player.getUuid(), player.getGameProfile().getName(), maxClaims);
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result abandon(ServerPlayerEntity player) {
        ClaimKey key = standing(player);
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.abandon(key.dimension(), key.chunkX(), key.chunkZ(), player.getUuid());
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result trust(ServerPlayerEntity player, java.util.UUID memberId, String memberName) {
        ClaimKey key = standing(player);
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.trust(key.dimension(), key.chunkX(), key.chunkZ(),
                player.getUuid(), memberId, memberName);
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result untrust(ServerPlayerEntity player, java.util.UUID memberId, String memberName) {
        ClaimKey key = standing(player);
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.untrust(key.dimension(), key.chunkX(), key.chunkZ(),
                player.getUuid(), memberId, memberName);
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result transfer(ServerPlayerEntity player, java.util.UUID newOwner, String newOwnerName, int maxClaims) {
        ClaimKey key = standing(player);
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.transfer(key.dimension(), key.chunkX(), key.chunkZ(),
                player.getUuid(), newOwner, newOwnerName, maxClaims);
        finish(renamed || result.succeeded());
        return result;
    }

    public void touch(ServerPlayerEntity player) {
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        finish(renamed);
    }

    /**
     * Store a marker on a claim for a later shop or debt integration.
     * This version never reads the value.
     */
    public void putFlag(Claim claim, String key, NbtElement value) {
        claim.putFlag(key, value);
        finish(true);
    }

    private void finish(boolean changed) {
        if (!changed) {
            return;
        }
        markDirty();
        ClaimSync.broadcast(server);
    }

    public static ClaimKey standing(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        return new ClaimKey(dimension, pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound written = claims.toNbt();
        for (String key : written.getKeys()) {
            NbtElement value = written.get(key);
            if (value != null) {
                nbt.put(key, value.copy());
            }
        }
        return nbt;
    }
}
