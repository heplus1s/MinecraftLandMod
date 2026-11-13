package dev.jdguo.land.api;

import dev.jdguo.land.claim.Claim;
import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.ClaimMap;
import dev.jdguo.land.claim.ParcelInfo;
import dev.jdguo.land.config.LandConfig;
import dev.jdguo.land.integration.ClaimGate;
import dev.jdguo.land.integration.ClaimGates;
import dev.jdguo.land.integration.LandEvents;
import dev.jdguo.land.integration.LandListener;
import dev.jdguo.land.world.ClaimSavedData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side entry point for a bank or market mod.
 * Call it on the server thread. Protection and saving stay inside this mod.
 * <p>
 * Area for valuation is the parcel's chunk count. Each chunk is one 16×16 column,
 * so {@link ParcelInfo#blockCount()} is {@code chunkCount * 256}.
 * Negative block coordinates belong to a chunk via an arithmetic shift ({@code block >> 4}),
 * which {@link #parcelAtBlock} already does.
 * <p>
 * Trade: register a {@link ClaimGate} that vetoes {@code allowParcelTransfer},
 * {@code allowTransfer}, and {@code allowSplit} while a parcel is listed or locked.
 * Pass the escrow id as {@code reference} when calling {@link #transferParcel} or
 * {@link #transferChunk}; the gate can allow that reference and reject an empty one.
 * Listen with {@link LandEvents#register} to settle after the owner changes.
 * <p>
 * Collateral: store a marker with {@link #putParcelFlag}. Veto abandon, split,
 * merge, and player transfer while it is set. {@link #seizeParcel} ignores the
 * current owner. Its {@code reference} is the loan id the gate can require.
 * There is no price and no currency in this mod.
 */
public final class LandApi {
    private LandApi() {
    }

    public static @Nullable ParcelInfo parcel(MinecraftServer server, UUID parcelId) {
        return claims(server).info(parcelId);
    }

    public static @Nullable ParcelInfo parcelAt(MinecraftServer server, String dimension, int chunkX, int chunkZ) {
        Claim claim = claims(server).get(dimension, chunkX, chunkZ);
        if (claim == null) {
            return null;
        }
        return claims(server).info(claim.parcelId());
    }

    /**
     * The parcel covering this block column, or null when the chunk is free.
     */
    public static @Nullable ParcelInfo parcelAtBlock(MinecraftServer server, String dimension, int blockX, int blockZ) {
        return parcelAt(server, dimension, blockX >> 4, blockZ >> 4);
    }

    public static @Nullable Claim claimAt(MinecraftServer server, String dimension, int chunkX, int chunkZ) {
        return claims(server).get(dimension, chunkX, chunkZ);
    }

    public static List<ParcelInfo> parcelsOf(MinecraftServer server, UUID owner) {
        return claims(server).parcelsOwnedBy(owner);
    }

    public static int chunkCount(MinecraftServer server, UUID parcelId) {
        return claims(server).parcelChunkCount(parcelId);
    }

    public static long blockCount(MinecraftServer server, UUID parcelId) {
        return claims(server).parcelBlockCount(parcelId);
    }

    public static int ownedChunkCount(MinecraftServer server, UUID owner) {
        return claims(server).countOwned(owner);
    }

    public static int ownedParcelCount(MinecraftServer server, UUID owner) {
        return claims(server).countParcelsOwned(owner);
    }

    public static NbtCompound parcelFlags(MinecraftServer server, UUID parcelId) {
        return claims(server).copyParcelFlags(parcelId);
    }

    public static boolean putParcelFlag(MinecraftServer server, UUID parcelId, String key, NbtElement value) {
        return saved(server).putParcelFlag(parcelId, key, value);
    }

    public static void putChunkFlag(MinecraftServer server, Claim claim, String key, NbtElement value) {
        saved(server).putFlag(claim, key, value);
    }

    /**
     * Gift or sell a whole parcel. Fails when the buyer would pass the chunk cap.
     * {@code reference} is stored only for the gate and the listener on this call.
     */
    public static ClaimMap.Result transferParcel(MinecraftServer server, UUID actor, UUID parcelId, UUID newOwner, String newOwnerName, String reference) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(parcelId, "parcel");
        Objects.requireNonNull(newOwner, "newOwner");
        return saved(server).transferParcel(actor, parcelId, newOwner, newOwnerName, LandConfig.maxClaimsPerPlayer(), reference);
    }

    /**
     * Gift or sell one chunk. A chunk that is not alone in its parcel is carved
     * into a new parcel for the buyer. The last chunk transfers that parcel as-is.
     */
    public static ClaimMap.Result transferChunk(MinecraftServer server, UUID actor, String dimension, int chunkX, int chunkZ, UUID newOwner, String newOwnerName, String reference) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(newOwner, "newOwner");
        return saved(server).transferChunk(actor, dimension, chunkX, chunkZ, newOwner, newOwnerName, LandConfig.maxClaimsPerPlayer(), reference);
    }

    public static ClaimMap.Result transferChunks(MinecraftServer server, UUID actor, List<ClaimKey> chunks, UUID newOwner, String newOwnerName, String reference) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(newOwner, "newOwner");
        return saved(server).transferChunks(actor, chunks, newOwner, newOwnerName, LandConfig.maxClaimsPerPlayer(), reference);
    }

    /**
     * Carve chunks the actor owns into a new parcel they still own.
     * One chunk of the source parcel has to remain.
     */
    public static ClaimMap.Result split(MinecraftServer server, UUID actor, List<ClaimKey> chunks, String newName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(chunks, "chunks");
        return saved(server).split(actor, chunks, newName);
    }

    /**
     * Move a parcel to {@code newOwner} without the current owner acting.
     * When {@code respectLimit} is false the chunk cap is not applied.
     */
    public static ClaimMap.Result seizeParcel(MinecraftServer server, UUID parcelId, UUID newOwner, String newOwnerName, boolean respectLimit, String reference) {
        Objects.requireNonNull(parcelId, "parcel");
        Objects.requireNonNull(newOwner, "newOwner");
        int maxClaims = respectLimit ? LandConfig.maxClaimsPerPlayer() : 0;
        return saved(server).seizeParcel(parcelId, newOwner, newOwnerName, maxClaims, reference);
    }

    public static ClaimMap.Result seizeChunk(MinecraftServer server, String dimension, int chunkX, int chunkZ, UUID newOwner, String newOwnerName, boolean respectLimit, String reference) {
        Objects.requireNonNull(newOwner, "newOwner");
        int maxClaims = respectLimit ? LandConfig.maxClaimsPerPlayer() : 0;
        return saved(server).seizeChunk(dimension, chunkX, chunkZ, newOwner, newOwnerName, maxClaims, reference);
    }

    public static void registerGate(ClaimGate gate) {
        ClaimGates.register(gate);
    }

    public static void registerListener(LandListener listener) {
        LandEvents.register(listener);
    }

    private static ClaimSavedData saved(MinecraftServer server) {
        return ClaimSavedData.get(Objects.requireNonNull(server, "server"));
    }

    private static ClaimMap claims(MinecraftServer server) {
        return saved(server).claims();
    }
}
