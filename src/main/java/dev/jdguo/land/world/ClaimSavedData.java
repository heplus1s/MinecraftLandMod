package dev.jdguo.land.world;

import dev.jdguo.land.claim.Claim;
import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.ClaimMap;
import dev.jdguo.land.claim.GovernmentOwner;
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
    private NbtCompound initialGrants = new NbtCompound();
    private NbtCompound marketTransfers = new NbtCompound();
    private final java.util.Set<String> unflushedGrants = new java.util.HashSet<>();

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
        data.initialGrants = nbt.getCompound("InitialGrants").copy();
        data.marketTransfers = nbt.getCompound("MarketTransfers").copy();
        if (loaded.needsRewrite()) {
            data.markDirty();
        }
        return data;
    }

    public String initialGrantStatus(String reference) {
        if (!initialGrants.contains(reference)) return "missing";
        return unflushedGrants.contains(reference) ? "pending" : "applied";
    }

    public String initialGrant(UUID buyer, List<ClaimKey> keys, String fingerprint, String reference) {
        if (reference.startsWith("government:")) return "rejected:reference";
        if (initialGrants.contains(reference)) {
            if (!initialGrants.getString(reference).equals(fingerprint)) return "conflict";
            return flushGrant(reference) ? "applied" : "pending";
        }
        String name = server.getPlayerManager().getPlayer(buyer) == null ? buyer.toString()
                : server.getPlayerManager().getPlayer(buyer).getGameProfile().getName();
        var result = claims.initialGrant(keys, buyer, name, dev.jdguo.land.config.LandConfig.maxClaimsPerPlayer());
        if (!result.succeeded()) return "rejected:" + result.subject();
        initialGrants.putString(reference, fingerprint);
        unflushedGrants.add(reference);
        markDirty();
        boolean durable = flushGrant(reference);
        ClaimSync.broadcast(server);
        LandTrade.broadcast(server);
        return durable ? "applied" : "pending";
    }

    /** Administrator-only allocation. Does not use the personal grant or settlement entry points. */
    String allocateGovernment(ServerPlayerEntity operator, List<ClaimKey> keys, UUID token) {
        if (!server.isOnThread() || operator == null || token == null || !operator.hasPermissionLevel(2)
                || server.getPlayerManager().getPlayer(operator.getUuid()) != operator
                || keys == null || keys.isEmpty()
                || keys.stream().anyMatch(key -> !operator.getWorld().getRegistryKey().getValue().toString().equals(key.dimension())))
            return "rejected:permission";
        String reference = "government:" + token;
        StringBuilder fingerprint = new StringBuilder(operator.getUuid().toString());
        for (ClaimKey key : keys) fingerprint.append('|').append(key.dimension()).append(':').append(key.chunkX()).append(',').append(key.chunkZ());
        if (initialGrants.contains(reference)) {
            if (!initialGrants.getString(reference).contentEquals(fingerprint)) return "conflict";
            return flushGrant(reference) ? "applied" : "pending";
        }
        var result = claims.initialGrant(keys, GovernmentOwner.ID, GovernmentOwner.NAME, 0);
        if (!result.succeeded()) return "rejected:" + result.subject();
        var metadata = new NbtCompound();
        metadata.putUuid("Operator", operator.getUuid());
        metadata.putString("OperatorName", operator.getGameProfile().getName());
        metadata.putLong("Time", System.currentTimeMillis());
        metadata.putString("Reference", reference);
        claims.putParcelFlag(result.claim().parcelId(), "government_allocation", metadata);
        initialGrants.putString(reference, fingerprint.toString());
        unflushedGrants.add(reference);
        markDirty();
        boolean durable = flushGrant(reference);
        ClaimSync.broadcast(server);
        LandTrade.broadcast(server);
        return durable ? "applied" : "pending";
    }

    ClaimMap.Result renameGovernment(ServerPlayerEntity player, UUID parcelId, String name) {
        var current = claims.info(parcelId);
        if (!player.hasPermissionLevel(2) || current == null || !GovernmentOwner.is(current.owner()))
            return ClaimMap.fail(ClaimMap.Denial.NOT_OWNER, null, "");
        var result = claims.renameParcel(GovernmentOwner.ID, parcelId, name);
        finish(result.succeeded());
        return result;
    }

    /** Atomically replace the same PersistentState file, acknowledge only after read-back. */
    private boolean flushGrant(String reference) {
        java.nio.file.Path file = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("data").resolve(ID + ".dat");
        java.nio.file.Path temp = file.resolveSibling(ID + ".initial.tmp");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            NbtCompound root = new NbtCompound();
            root.put("data", writeNbt(new NbtCompound()));
            root.putInt("DataVersion", net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            net.minecraft.nbt.NbtIo.writeCompressed(root, temp.toFile());
            try (var channel = java.nio.channels.FileChannel.open(temp, java.nio.file.StandardOpenOption.WRITE)) { channel.force(true); }
            java.nio.file.Files.move(temp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            var check = net.minecraft.nbt.NbtIo.readCompressed(file.toFile());
            if (!check.getCompound("data").getCompound("InitialGrants").getString(reference).equals(initialGrants.getString(reference))) return false;
            unflushedGrants.clear();
            setDirty(false);
            return true;
        } catch (java.io.IOException | RuntimeException failure) {
            unflushedGrants.add(reference);
            dev.jdguo.land.LandMod.LOGGER.error("Initial land grant awaiting durable save: {}", reference, failure);
            markDirty();
            return false;
        }
    }

    /** A receipt is committed in the same file as ownership, before market may forget its PAID journal. */
    public String marketTransfer(UUID actor, UUID buyer, String buyerName, UUID parcelId,
                                 List<ClaimKey> keys, boolean whole, String reference) {
        if (keys == null || keys.isEmpty() || keys.size() > 1024 || keys.stream().anyMatch(java.util.Objects::isNull)
                || new java.util.HashSet<>(keys).size() != keys.size()) return "rejected:selection";
        List<ClaimKey> canonical = keys.stream().sorted(java.util.Comparator.comparing(ClaimKey::dimension)
                .thenComparingInt(ClaimKey::chunkX).thenComparingInt(ClaimKey::chunkZ)).toList();
        StringBuilder terms = new StringBuilder(actor + "|" + buyer + "|" + parcelId + "|" + whole);
        for (ClaimKey key : canonical) terms.append('|').append(key.dimension().length()).append(':')
                .append(key.dimension()).append(':').append(key.chunkX()).append(',').append(key.chunkZ());
        String fingerprint = terms.toString();
        if (marketTransfers.contains(reference)) {
            if (!fingerprint.equals(marketTransfers.getString(reference))) return "conflict";
            return flushMarketTransfer(reference) ? "applied" : "pending";
        }
        // Legacy PAID recovery may already have performed its handoff. Never refund or replay
        // from current ownership alone: somebody may have transferred the land again meanwhile.
        boolean sellerOwns = canonical.stream().allMatch(key -> {
            Claim claim = claims.get(key.dimension(), key.chunkX(), key.chunkZ());
            return claim != null && actor.equals(claim.owner()) && parcelId.equals(claim.parcelId());
        });
        if (!sellerOwns) return "pending";
        if (whole) {
            var parcel = claims.info(parcelId);
            if (parcel == null || !new java.util.HashSet<>(parcel.chunks()).equals(new java.util.HashSet<>(canonical)))
                return "rejected:SALE_STALE";
        }
        ClaimMap.Result result;
        try {
            int maxClaims = GovernmentOwner.is(buyer) ? 0 : dev.jdguo.land.config.LandConfig.maxClaimsPerPlayer();
            String ownerName = GovernmentOwner.is(buyer) ? GovernmentOwner.NAME : buyerName;
            result = whole ? claims.transferParcel(actor, parcelId, buyer, ownerName, maxClaims, reference)
                    : claims.transferChunks(actor, canonical, buyer, ownerName, maxClaims, reference);
        } catch (RuntimeException unknown) {
            // A listener may throw after ownership changed. Do not make a false rejection.
            markDirty();
            return "pending";
        }
        if (!result.succeeded()) return "rejected:" + result.denial();
        claims.sales(); // Match ordinary transfer cleanup before persisting the ownership receipt.
        marketTransfers.putString(reference, fingerprint);
        markDirty();
        boolean durable = flushMarketTransfer(reference);
        ClaimSync.broadcast(server);
        LandTrade.broadcast(server);
        return durable ? "applied" : "pending";
    }

    private boolean flushMarketTransfer(String reference) {
        java.nio.file.Path file = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("data").resolve(ID + ".dat");
        try {
            DurableLandState.save(file, writeNbt(new NbtCompound()),
                    net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId());
            unflushedGrants.clear();
            setDirty(false);
            return true;
        } catch (java.io.IOException | RuntimeException failure) {
            markDirty();
            dev.jdguo.land.LandMod.LOGGER.error("Land transfer awaiting durable save: {}", reference, failure);
            return false;
        }
    }

    public ClaimMap claims() {
        return claims;
    }

    public ClaimMap.Result claim(ServerPlayerEntity player, int maxClaims) {
        ClaimKey key = standing(player);
        if (dev.jdguo.land.integration.MarketTradeBridge.initialEnabled(server)) {
            return marketChunks(player, List.of(key), "buy");
        }
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

    /** Explicit map target. Ownership is checked by ClaimMap for every mutation. */
    public ClaimMap.Result setMember(ServerPlayerEntity player, ClaimKey key, UUID memberId, String memberName, boolean add) {
        var claim = claims.get(key.dimension(), key.chunkX(), key.chunkZ());
        if (claim == null || !GovernmentOwner.mayManage(player.getUuid(), claim.owner(), player.hasPermissionLevel(2)))
            return ClaimMap.fail(ClaimMap.Denial.NOT_OWNER, claim, "");
        UUID actor = claim.owner();
        ClaimMap.Result result = add
                ? claims.trust(key.dimension(), key.chunkX(), key.chunkZ(), actor, memberId, memberName)
                : claims.untrust(key.dimension(), key.chunkX(), key.chunkZ(), actor, memberId, memberName);
        finish(result.succeeded());
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
        if (dev.jdguo.land.integration.MarketTradeBridge.initialEnabled(server)) {
            player.sendMessage(dev.jdguo.land.message.LandMessages.chat("此服务器已启用付费初售。请先购买新区块，再合并宗地。"), false);
            return marketChunks(player, List.of(key), "buy");
        }
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
        if (keys == null || keys.isEmpty()) {
            player.sendMessage(dev.jdguo.land.message.LandMessages.chat("还没有选中区块。"), false);
            return new ClaimMap.Result(ClaimMap.Denial.NOT_CLAIMED, null, "");
        }
        var first = claims.get(keys.get(0).dimension(), keys.get(0).chunkX(), keys.get(0).chunkZ());
        if (first == null && action.equals("buy") && keys.stream().allMatch(key -> claims.get(key.dimension(), key.chunkX(), key.chunkZ()) == null)) {
            return market(player, null, "buy", keys);
        }
        if (first == null) {
            player.sendMessage(dev.jdguo.land.message.LandMessages.chat(action.equals("sell") ? "选中的是无主地块，不能出售。请先购买取得所有权，再选择自己的领地出售。" : "选区混合了无主地块与已有领地，请分别选择购买。"), false);
            return new ClaimMap.Result(ClaimMap.Denial.NOT_CLAIMED, null, "");
        }
        for (ClaimKey key : keys) {
            var claim = claims.get(key.dimension(), key.chunkX(), key.chunkZ());
            if (claim == null || !claim.parcelId().equals(first.parcelId())) {
                player.sendMessage(dev.jdguo.land.message.LandMessages.chat("请只选择同一宗地内的区块；不同宗地需分别交易。"), false);
                return new ClaimMap.Result(ClaimMap.Denial.MIXED_PARCEL, null, "");
            }
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
        nbt.put("InitialGrants", initialGrants.copy());
        nbt.put("MarketTransfers", marketTransfers.copy());
        return nbt;
    }
}
