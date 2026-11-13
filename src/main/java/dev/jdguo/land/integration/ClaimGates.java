package dev.jdguo.land.integration;

import dev.jdguo.land.claim.Claim;
import dev.jdguo.land.claim.ParcelInfo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClaimGates {
    private static final List<ClaimGate> GATES = new CopyOnWriteArrayList<>();

    private ClaimGates() {
    }

    public static void register(ClaimGate gate) {
        GATES.add(Objects.requireNonNull(gate, "gate"));
    }

    public static void resetForTests() {
        GATES.clear();
    }

    public static boolean allowClaim(UUID playerId, String dimension, int chunkX, int chunkZ) {
        for (ClaimGate gate : GATES) {
            if (!gate.allowClaim(playerId, dimension, chunkX, chunkZ)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowJoin(UUID playerId, ParcelInfo parcel, String dimension, int chunkX, int chunkZ) {
        for (ClaimGate gate : GATES) {
            if (!gate.allowJoin(playerId, parcel, dimension, chunkX, chunkZ)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowAbandon(UUID actorId, Claim claim) {
        for (ClaimGate gate : GATES) {
            if (!gate.allowAbandon(actorId, claim)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowTransfer(UUID actorId, Claim claim, UUID newOwnerId) {
        return allowTransfer(actorId, claim, newOwnerId, "");
    }

    public static boolean allowTransfer(UUID actorId, Claim claim, UUID newOwnerId, String reference) {
        String ref = reference == null ? "" : reference;
        for (ClaimGate gate : GATES) {
            if (!gate.allowTransfer(actorId, claim, newOwnerId, ref)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowParcelTransfer(UUID actorId, ParcelInfo parcel, UUID newOwnerId, String reference) {
        String ref = reference == null ? "" : reference;
        for (ClaimGate gate : GATES) {
            if (!gate.allowParcelTransfer(actorId, parcel, newOwnerId, ref)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowSplit(UUID actorId, ParcelInfo parcel, List<Claim> chunks) {
        for (ClaimGate gate : GATES) {
            if (!gate.allowSplit(actorId, parcel, chunks)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowMerge(UUID actorId, ParcelInfo target, ParcelInfo source) {
        for (ClaimGate gate : GATES) {
            if (!gate.allowMerge(actorId, target, source)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowRename(UUID actorId, ParcelInfo parcel, String newName) {
        for (ClaimGate gate : GATES) {
            if (!gate.allowRename(actorId, parcel, newName)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowTrust(UUID actorId, Claim claim, UUID memberId) {
        for (ClaimGate gate : GATES) {
            if (!gate.allowTrust(actorId, claim, memberId)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowUntrust(UUID actorId, Claim claim, UUID memberId) {
        for (ClaimGate gate : GATES) {
            if (!gate.allowUntrust(actorId, claim, memberId)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowSeizeParcel(ParcelInfo parcel, UUID newOwnerId, String reference) {
        String ref = reference == null ? "" : reference;
        for (ClaimGate gate : GATES) {
            if (!gate.allowSeizeParcel(parcel, newOwnerId, ref)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowSeizeChunk(Claim claim, UUID newOwnerId, String reference) {
        String ref = reference == null ? "" : reference;
        for (ClaimGate gate : GATES) {
            if (!gate.allowSeizeChunk(claim, newOwnerId, ref)) {
                return false;
            }
        }
        return true;
    }
}
