package dev.jdguo.land.integration;

import dev.jdguo.land.claim.Claim;
import dev.jdguo.land.claim.ParcelInfo;

import java.util.List;
import java.util.UUID;

/**
 * Fired after a parcel change has already been stored.
 * Do not throw. A market can settle escrow here, and a loan mod can record a seizure.
 * Callers are on the server thread. Avoid starting another land mutation from the
 * same listener unless it is a different parcel.
 */
public interface LandListener {
    /**
     * The whole parcel changed owner, including a transfer of its only chunk.
     * Seizure uses {@link #afterSeizeParcel} instead.
     */
    default void afterParcelTransfer(ParcelInfo parcel, UUID previousOwner, UUID newOwner, String reference) {
    }

    /**
     * Some, but not all, chunks left {@code previousParcelId} and now form {@code parcel}.
     */
    default void afterChunkTransfer(List<Claim> chunks, UUID previousParcelId, ParcelInfo parcel, UUID previousOwner, UUID newOwner, String reference) {
    }

    default void afterSplit(ParcelInfo remainder, ParcelInfo created) {
    }

    default void afterMerge(ParcelInfo target, UUID removedParcelId) {
    }

    default void afterSeizeParcel(ParcelInfo parcel, UUID previousOwner, UUID newOwner, String reference) {
    }

    /**
     * One chunk was seized out of a larger parcel. Seizing the last chunk is
     * {@link #afterSeizeParcel}.
     */
    default void afterSeizeChunk(Claim chunk, UUID previousParcelId, ParcelInfo parcel, UUID previousOwner, UUID newOwner, String reference) {
    }
}
