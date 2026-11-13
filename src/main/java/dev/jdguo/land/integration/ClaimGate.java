package dev.jdguo.land.integration;

import dev.jdguo.land.claim.Claim;

import java.util.UUID;

/**
 * Veto hook for a later bank-debt or shop integration.
 * Phase 1 does not register any gate and does not talk to an economy mod.
 * Return false to stop that one action. The chunk's {@code flags} compound is
 * the place to store debt or shop markers; this version round-trips it.
 */
public interface ClaimGate {
    default boolean allowClaim(UUID playerId, String dimension, int chunkX, int chunkZ) {
        return true;
    }

    default boolean allowAbandon(UUID actorId, Claim claim) {
        return true;
    }

    default boolean allowTransfer(UUID actorId, Claim claim, UUID newOwnerId) {
        return true;
    }

    default boolean allowTrust(UUID actorId, Claim claim, UUID memberId) {
        return true;
    }

    default boolean allowUntrust(UUID actorId, Claim claim, UUID memberId) {
        return true;
    }
}
