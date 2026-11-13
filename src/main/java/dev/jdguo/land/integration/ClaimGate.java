package dev.jdguo.land.integration;

import dev.jdguo.land.claim.Claim;
import dev.jdguo.land.claim.ParcelInfo;

import java.util.List;
import java.util.UUID;

/**
 * Veto hook for bank, escrow, or shop code. This mod does not register a gate.
 * Return false to stop that one action. The parcel and chunk {@code flags}
 * compounds are the place to store collateral or escrow markers.
 * <p>
 * A one-chunk parcel transfer is both {@link #allowParcelTransfer} and
 * {@link #allowTransfer}. Carving chunks out of a larger parcel, including a
 * carve that is then sold, also calls {@link #allowSplit}.
 * {@code reference} is empty for player commands. A market mod can pass an
 * escrow id, and a loan mod can pass a loan id to {@code seize}.
 */
public interface ClaimGate {
    default boolean allowClaim(UUID playerId, String dimension, int chunkX, int chunkZ) {
        return true;
    }

    default boolean allowJoin(UUID playerId, ParcelInfo parcel, String dimension, int chunkX, int chunkZ) {
        return true;
    }

    default boolean allowAbandon(UUID actorId, Claim claim) {
        return true;
    }

    default boolean allowTransfer(UUID actorId, Claim claim, UUID newOwnerId) {
        return true;
    }

    default boolean allowTransfer(UUID actorId, Claim claim, UUID newOwnerId, String reference) {
        return allowTransfer(actorId, claim, newOwnerId);
    }

    default boolean allowParcelTransfer(UUID actorId, ParcelInfo parcel, UUID newOwnerId, String reference) {
        return true;
    }

    default boolean allowSplit(UUID actorId, ParcelInfo parcel, List<Claim> chunks) {
        return true;
    }

    default boolean allowMerge(UUID actorId, ParcelInfo target, ParcelInfo source) {
        return true;
    }

    default boolean allowRename(UUID actorId, ParcelInfo parcel, String newName) {
        return true;
    }

    default boolean allowTrust(UUID actorId, Claim claim, UUID memberId) {
        return true;
    }

    default boolean allowUntrust(UUID actorId, Claim claim, UUID memberId) {
        return true;
    }

    default boolean allowSeizeParcel(ParcelInfo parcel, UUID newOwnerId, String reference) {
        return true;
    }

    default boolean allowSeizeChunk(Claim claim, UUID newOwnerId, String reference) {
        return true;
    }
}
