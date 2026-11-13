package dev.jdguo.land.integration;

import dev.jdguo.land.claim.Claim;

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

    public static boolean allowAbandon(UUID actorId, Claim claim) {
        for (ClaimGate gate : GATES) {
            if (!gate.allowAbandon(actorId, claim)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allowTransfer(UUID actorId, Claim claim, UUID newOwnerId) {
        for (ClaimGate gate : GATES) {
            if (!gate.allowTransfer(actorId, claim, newOwnerId)) {
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
}
