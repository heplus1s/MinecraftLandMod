package dev.jdguo.land.world;

import dev.jdguo.land.claim.Claim;
import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.ClaimMap;
import dev.jdguo.land.claim.SaleInfo;
import dev.jdguo.land.claim.SaleKind;
import dev.jdguo.land.network.ClaimSync;
import dev.jdguo.land.trade.LandTrade;
import dev.jdguo.land.trade.SaleChat;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * All dimensions share one file on the overworld: {@code data/landmod_claims.dat}.
 * Format 2 adds parcel ids. Format 1 files still load: each old chunk becomes its
 * own parcel, and unknown keys are written back.
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
                nbt -> load(server, nbt),
                () -> new ClaimSavedData(server, new ClaimMap()),
                ID);
    }

    private static ClaimSavedData load(MinecraftServer server, NbtCompound nbt) {
        ClaimMap loaded = ClaimMap.fromNbt(nbt);
        ClaimSavedData data = new ClaimSavedData(server, loaded);
        if (loaded.needsRewrite()) {
            data.markDirty();
        }
        return data;
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

    public ClaimMap.Result trust(ServerPlayerEntity player, UUID memberId, String memberName) {
        ClaimKey key = standing(player);
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.trust(key.dimension(), key.chunkX(), key.chunkZ(),
                player.getUuid(), memberId, memberName);
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result untrust(ServerPlayerEntity player, UUID memberId, String memberName) {
        ClaimKey key = standing(player);
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.untrust(key.dimension(), key.chunkX(), key.chunkZ(),
                player.getUuid(), memberId, memberName);
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result transfer(ServerPlayerEntity player, UUID newOwner, String newOwnerName, int maxClaims) {
        ClaimKey key = standing(player);
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.transfer(key.dimension(), key.chunkX(), key.chunkZ(),
                player.getUuid(), newOwner, newOwnerName, maxClaims);
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result claimJoin(ServerPlayerEntity player, UUID parcelId, int maxClaims) {
        ClaimKey key = standing(player);
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.claimJoin(key.dimension(), key.chunkX(), key.chunkZ(),
                player.getUuid(), player.getGameProfile().getName(), maxClaims, parcelId);
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result renameParcel(ServerPlayerEntity player, UUID parcelId, String name) {
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.renameParcel(player.getUuid(), parcelId, name);
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result merge(ServerPlayerEntity player, UUID targetId, UUID sourceId) {
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.merge(player.getUuid(), targetId, sourceId);
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result split(ServerPlayerEntity player, List<ClaimKey> chunks, String name) {
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.split(player.getUuid(), chunks, name);
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result split(UUID actor, List<ClaimKey> chunks, String name) {
        ClaimMap.Result result = claims.split(actor, chunks, name);
        finish(result.succeeded());
        return result;
    }

    public ClaimMap.Result transferChunks(ServerPlayerEntity player, List<ClaimKey> chunks, UUID newOwner, String newOwnerName, int maxClaims) {
        boolean renamed = claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = claims.transferChunks(player.getUuid(), chunks, newOwner, newOwnerName, maxClaims, "");
        finish(renamed || result.succeeded());
        return result;
    }

    public ClaimMap.Result transferChunks(UUID actor, List<ClaimKey> chunks, UUID newOwner, String newOwnerName, int maxClaims, String reference) {
        ClaimMap.Result result = claims.transferChunks(actor, chunks, newOwner, newOwnerName, maxClaims, reference);
        finish(result.succeeded());
        return result;
    }

    public ClaimMap.Result transferChunk(UUID actor, String dimension, int chunkX, int chunkZ, UUID newOwner, String newOwnerName, int maxClaims, String reference) {
        ClaimMap.Result result = claims.transfer(dimension, chunkX, chunkZ, actor, newOwner, newOwnerName, maxClaims, reference);
        finish(result.succeeded());
        return result;
    }

    public ClaimMap.Result transferParcel(UUID actor, UUID parcelId, UUID newOwner, String newOwnerName, int maxClaims, String reference) {
        ClaimMap.Result result = claims.transferParcel(actor, parcelId, newOwner, newOwnerName, maxClaims, reference);
        finish(result.succeeded());
        return result;
    }

    public ClaimMap.Result seizeParcel(UUID parcelId, UUID newOwner, String newOwnerName, int maxClaims, String reference) {
        ClaimMap.Result result = claims.seizeParcel(parcelId, newOwner, newOwnerName, maxClaims, reference);
        finish(result.succeeded());
        return result;
    }

    public ClaimMap.Result seizeChunk(String dimension, int chunkX, int chunkZ, UUID newOwner, String newOwnerName, int maxClaims, String reference) {
        ClaimMap.Result result = claims.seizeChunk(dimension, chunkX, chunkZ, newOwner, newOwnerName, maxClaims, reference);
        finish(result.succeeded());
        return result;
    }

    public boolean putParcelFlag(UUID parcelId, String key, NbtElement value) {
        boolean changed = claims.putParcelFlag(parcelId, key, value);
        finish(changed);
        return changed;
    }

    // All player-facing sale entry points use the market confirmation UI.
    // Opening a screen is not a successful sale: never clear selections or emit a paid receipt here.
    public ClaimMap.Result sellParcel(ServerPlayerEntity player, UUID parcelId) {
        return market(player, parcelId, "sell", List.of());
    }

    public ClaimMap.Result sellChunks(ServerPlayerEntity player, List<ClaimKey> keys) {
        return marketChunks(player, keys, "sell");
    }

    public ClaimMap.Result cancelSale(ServerPlayerEntity player, UUID parcelId) {
        return market(player, parcelId, "cancel", List.of());
    }

    public ClaimMap.Result buyParcel(ServerPlayerEntity player, UUID parcelId) {
        return market(player, parcelId, "buy", List.of());
    }

    public ClaimMap.Result buyChunks(ServerPlayerEntity player, List<ClaimKey> keys) {
        return marketChunks(player, keys, "buy");
    }

    public ClaimMap.Result buyStanding(ServerPlayerEntity player) {
        return marketChunks(player, List.of(standing(player)), "buy");
    }

    private ClaimMap.Result marketChunks(ServerPlayerEntity player, List<ClaimKey> keys, String action) {
        if (keys == null || keys.isEmpty()) return new ClaimMap.Result(ClaimMap.Denial.NOT_CLAIMED, null, "");
        var first = claims.get(keys.get(0).dimension(), keys.get(0).chunkX(), keys.get(0).chunkZ());
        if (first == null) return new ClaimMap.Result(ClaimMap.Denial.NOT_CLAIMED, null, "");
        for (ClaimKey key : keys) {
            var claim = claims.get(key.dimension(), key.chunkX(), key.chunkZ());
            if (claim == null || !claim.parcelId().equals(first.parcelId()))
                return new ClaimMap.Result(ClaimMap.Denial.MIXED_PARCEL, null, "");
        }
        return market(player, first.parcelId(), action, keys);
    }

    private ClaimMap.Result market(ServerPlayerEntity player, UUID parcelId, String action, List<ClaimKey> keys) {
        dev.jdguo.land.integration.MarketTradeBridge.open(player, parcelId, action, keys);
        return new ClaimMap.Result(ClaimMap.Denial.GATE, null, "market_confirmation");
    }

    private SaleInfo saleOfKey(ClaimKey key) {
        dev.jdguo.land.claim.Claim claim = claims.get(key.dimension(), key.chunkX(), key.chunkZ());
        if (claim == null) {
            return null;
        }
        return claims.saleOf(claim.parcelId());
    }

    private ClaimMap.Result tracked(ServerPlayerEntity player, java.util.function.Supplier<ClaimMap.Result> action, java.util.function.Consumer<ClaimMap.Result> report) {
        int claimsBefore = claims.revision();
        int salesBefore = claims.saleRevision();
        claims.updateName(player.getUuid(), player.getGameProfile().getName());
        ClaimMap.Result result = action.get();
        claims.sales();
        if (claims.revision() != claimsBefore) {
            markDirty();
            ClaimSync.broadcast(server);
        }
        if (claims.saleRevision() != salesBefore) {
            markDirty();
            LandTrade.broadcast(server);
        }
        report.accept(result);
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
        // Ordinary transfers, abandon, join, split and merge can invalidate offers.
        claims.sales();
        markDirty();
        ClaimSync.broadcast(server);
        LandTrade.broadcast(server);
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
