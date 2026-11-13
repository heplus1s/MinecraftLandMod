package dev.jdguo.land.integration;

import dev.jdguo.land.LandMod;
import dev.jdguo.land.claim.Claim;
import dev.jdguo.land.claim.ParcelInfo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class LandEvents {
    private static final List<LandListener> LISTENERS = new CopyOnWriteArrayList<>();

    private LandEvents() {
    }

    public static void register(LandListener listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    public static void resetForTests() {
        LISTENERS.clear();
    }

    public static void afterParcelTransfer(ParcelInfo parcel, UUID previousOwner, UUID newOwner, String reference) {
        fire(listener -> listener.afterParcelTransfer(parcel, previousOwner, newOwner, reference));
    }

    public static void afterChunkTransfer(List<Claim> chunks, UUID previousParcelId, ParcelInfo parcel, UUID previousOwner, UUID newOwner, String reference) {
        fire(listener -> listener.afterChunkTransfer(chunks, previousParcelId, parcel, previousOwner, newOwner, reference));
    }

    public static void afterSplit(ParcelInfo remainder, ParcelInfo created) {
        fire(listener -> listener.afterSplit(remainder, created));
    }

    public static void afterMerge(ParcelInfo target, UUID removedParcelId) {
        fire(listener -> listener.afterMerge(target, removedParcelId));
    }

    public static void afterSeizeParcel(ParcelInfo parcel, UUID previousOwner, UUID newOwner, String reference) {
        fire(listener -> listener.afterSeizeParcel(parcel, previousOwner, newOwner, reference));
    }

    public static void afterSeizeChunk(Claim chunk, UUID previousParcelId, ParcelInfo parcel, UUID previousOwner, UUID newOwner, String reference) {
        fire(listener -> listener.afterSeizeChunk(chunk, previousParcelId, parcel, previousOwner, newOwner, reference));
    }

    private static void fire(Consumer<LandListener> call) {
        for (LandListener listener : LISTENERS) {
            try {
                call.accept(listener);
            } catch (Exception exception) {
                LandMod.LOGGER.error("Land listener failed", exception);
            }
        }
    }
}
