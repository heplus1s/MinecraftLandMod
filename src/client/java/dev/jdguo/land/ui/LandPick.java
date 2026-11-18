package dev.jdguo.land.ui;

import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.client.ClientClaims;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunks the player has picked for a purchase or a listing.
 * A parcel pick selects every synced chunk of that parcel. Nothing smaller than a chunk is stored.
 */
public final class LandPick {
    public enum Mode {
        CHUNK,
        PARCEL
    }

    private static final List<ClaimKey> SELECTED = new ArrayList<>();
    private static Mode mode = Mode.CHUNK;
    private static boolean active;
    private static boolean contextRequired;
    public static boolean requiresContext() { return contextRequired; }
    public static void requireContext() { contextRequired = true; }
    private static int generation = 1;

    private LandPick() {
    }

    public static boolean active() {
        return active;
    }

    public static void toggle() {
        active = !active;
        changed();
    }

    public static Mode mode() {
        return mode;
    }

    public static void toggleMode() {
        mode = mode == Mode.CHUNK ? Mode.PARCEL : Mode.CHUNK;
        changed();
    }

    public static List<ClaimKey> selected() {
        return List.copyOf(SELECTED);
    }

    public static boolean contains(String dimension, int chunkX, int chunkZ) {
        return SELECTED.contains(new ClaimKey(dimension, chunkX, chunkZ));
    }

    public static int generation() {
        return generation;
    }

    public static void clear() {
        contextRequired = false;
        if (SELECTED.isEmpty()) {
            return;
        }
        SELECTED.clear();
        changed();
    }

    /** Never carry a hidden selection from a different map/world into a transaction. */
    public static void retainDimension(String dimension) {
        if (SELECTED.removeIf(key -> !key.dimension().equals(dimension))) {
            if (SELECTED.isEmpty()) contextRequired = false;
            changed();
        }
    }

    public static void click(String dimension, int chunkX, int chunkZ) {
        retainDimension(dimension);
        if (mode == Mode.PARCEL) {
            ClientClaims.Entry entry = ClientClaims.get(dimension, chunkX, chunkZ);
            if (entry == null) {
                toggleOne(new ClaimKey(dimension, chunkX, chunkZ));
            } else {
                List<ClientClaims.Entry> entries = ClientClaims.parcelEntries(dimension, entry.parcelId());
                boolean all = !entries.isEmpty();
                for (ClientClaims.Entry piece : entries) {
                    if (!contains(dimension, piece.chunkX(), piece.chunkZ())) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    for (ClientClaims.Entry piece : entries) {
                        SELECTED.remove(new ClaimKey(dimension, piece.chunkX(), piece.chunkZ()));
                    }
                } else {
                    for (ClientClaims.Entry piece : entries) {
                        ClaimKey key = new ClaimKey(dimension, piece.chunkX(), piece.chunkZ());
                        if (!SELECTED.contains(key)) {
                            SELECTED.add(key);
                        }
                    }
                }
            }
        } else {
            toggleOne(new ClaimKey(dimension, chunkX, chunkZ));
        }
        changed();
    }

    private static void toggleOne(ClaimKey key) {
        if (!SELECTED.remove(key)) {
            SELECTED.add(key);
        }
    }

    private static void changed() {
        generation++;
        MapRefresh.mark();
    }
}
