package dev.jdguo.land.command;

import dev.jdguo.land.claim.ClaimKey;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chunks a player has marked for a later split or chunk transfer.
 * The set is forgotten when they disconnect and is not written to the save.
 */
public final class ChunkMarks {
    public static final int MAX = 512;

    private static final Map<UUID, LinkedHashSet<ClaimKey>> MARKS = new HashMap<>();

    private ChunkMarks() {
    }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MARKS.remove(handler.player.getUuid()));
    }

    public static Change toggle(UUID playerId, ClaimKey key) {
        LinkedHashSet<ClaimKey> marks = MARKS.computeIfAbsent(playerId, id -> new LinkedHashSet<>());
        if (marks.remove(key)) {
            if (marks.isEmpty()) {
                MARKS.remove(playerId);
            }
            return Change.REMOVED;
        }
        if (marks.size() >= MAX) {
            return Change.FULL;
        }
        marks.add(key);
        return Change.ADDED;
    }

    public static void remove(UUID playerId, ClaimKey key) {
        LinkedHashSet<ClaimKey> marks = MARKS.get(playerId);
        if (marks == null) {
            return;
        }
        marks.remove(key);
        if (marks.isEmpty()) {
            MARKS.remove(playerId);
        }
    }

    public static int clear(UUID playerId) {
        LinkedHashSet<ClaimKey> marks = MARKS.remove(playerId);
        return marks == null ? 0 : marks.size();
    }

    public static List<ClaimKey> get(UUID playerId) {
        LinkedHashSet<ClaimKey> marks = MARKS.get(playerId);
        if (marks == null) {
            return List.of();
        }
        return List.copyOf(marks);
    }

    public static int size(UUID playerId) {
        LinkedHashSet<ClaimKey> marks = MARKS.get(playerId);
        return marks == null ? 0 : marks.size();
    }

    public static void resetForTests() {
        MARKS.clear();
    }

    public enum Change {
        ADDED,
        REMOVED,
        FULL
    }
}
