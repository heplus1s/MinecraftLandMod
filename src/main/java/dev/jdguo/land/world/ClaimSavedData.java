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

    public ClaimMap.Result sellParcel(ServerPlayerEntity player, UUID parcelId) {
        return tracked(player, () -> claims.sellParcel(player.getUuid(), player.getGameProfile().getName(), parcelId), (result) -> {
            SaleInfo sale = claims.saleOf(parcelId);
            int chunks = sale == null ? 0 : sale.chunks().size();
            SaleChat.Verb verb = sale != null && sale.kind() == SaleKind.CHUNKS ? SaleChat.Verb.SELL_CHUNKS : SaleChat.Verb.SELL_PARCEL;
            SaleChat.reply(player, result, verb, chunks, null, null);
        });
    }

    public ClaimMap.Result sellChunks(ServerPlayerEntity player, List<ClaimKey> keys) {
        return tracked(player, () -> claims.sellChunks(player.getUuid(), player.getGameProfile().getName(), keys), (result) -> {
            int chunks = keys == null ? 0 : keys.size();
            if (result.succeeded() && result.claim() != null) {
                SaleInfo sale = claims.saleOf(result.claim().parcelId());
                if (sale != null) {
                    chunks = sale.chunks().size();
                    SaleChat.reply(player, result, sale.kind() == SaleKind.PARCEL ? SaleChat.Verb.SELL_PARCEL : SaleChat.Verb.SELL_CHUNKS, chunks, null, null);
                    return;
                }
            }
            SaleChat.reply(player, result, SaleChat.Verb.SELL_CHUNKS, chunks, null, null);
        });
    }

    public ClaimMap.Result cancelSale(ServerPlayerEntity player, UUID parcelId) {
        return tracked(player, () -> claims.cancelSale(player.getUuid(), parcelId),
                result -> SaleChat.reply(player, result, SaleChat.Verb.CANCEL, 0, null, null));
    }

    public ClaimMap.Result buyParcel(ServerPlayerEntity player, UUID parcelId) {
        SaleInfo before = claims.saleOf(parcelId);
        int chunks = before == null ? 0 : before.chunks().size();
        UUID seller = before == null ? null : before.seller();
        String sellerName = before == null ? "" : before.sellerName();
        return tracked(player, () -> claims.buyParcel(player.getUuid(), player.getGameProfile().getName(), parcelId, dev.jdguo.land.config.LandConfig.maxClaimsPerPlayer()),
                result -> SaleChat.reply(player, result, SaleChat.Verb.BUY_PARCEL, chunks, seller, sellerName));
    }

    public ClaimMap.Result buyChunks(ServerPlayerEntity player, List<ClaimKey> keys) {
        SaleInfo before = keys == null || keys.isEmpty() ? null : saleOfKey(keys.get(0));
        int chunks = keys == null ? 0 : keys.size();
        UUID seller = before == null ? null : before.seller();
        String sellerName = before == null ? "" : before.sellerName();
        SaleChat.Verb verb = before != null && before.kind() == SaleKind.PARCEL ? SaleChat.Verb.BUY_PARCEL : SaleChat.Verb.BUY_CHUNKS;
        int reported = before != null && before.kind() == SaleKind.PARCEL ? before.chunks().size() : chunks;
        return tracked(player, () -> claims.buyChunks(player.getUuid(), player.getGameProfile().getName(), keys, dev.jdguo.land.config.LandConfig.maxClaimsPerPlayer()),
                result -> SaleChat.reply(player, result, verb, reported, seller, sellerName));
    }

    public ClaimMap.Result buyStanding(ServerPlayerEntity player) {
        ClaimKey key = standing(player);
        dev.jdguo.land.claim.Claim claim = claims.get(key.dimension(), key.chunkX(), key.chunkZ());
        if (claim == null) {
            player.sendMessage(dev.jdguo.land.message.LandMessages.chat("此处没有领地。"), false);
            return new ClaimMap.Result(ClaimMap.Denial.NOT_CLAIMED, null, "");
        }
        SaleInfo sale = claims.saleOf(claim.parcelId());
        if (sale == null) {
            player.sendMessage(dev.jdguo.land.message.LandMessages.chat("这块地没有在出售。"), false);
            return new ClaimMap.Result(ClaimMap.Denial.NOT_FOR_SALE, claim, "");
        }
        if (sale.kind() == SaleKind.PARCEL) {
            return buyParcel(player, sale.parcelId());
        }
        if (!sale.chunks().contains(key)) {
            player.sendMessage(dev.jdguo.land.message.LandMessages.chat("这块地没有在出售。"), false);
            return new ClaimMap.Result(ClaimMap.Denial.NOT_FOR_SALE, claim, "");
        }
        return buyChunks(player, List.of(key));
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
