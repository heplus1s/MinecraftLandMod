package dev.jdguo.land.client;

import dev.jdguo.land.network.ClaimSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client copy of claimed chunks, replaced as a whole when the server sends a snapshot.
 */
public final class ClientClaims {
    private static volatile View view = View.EMPTY;

    private ClientClaims() {
    }

    public static void replace(ClaimSnapshot snapshot) {
        view = View.from(snapshot);
    }

    public static Entry get(String dimension, int chunkX, int chunkZ) {
        return view.get(dimension, chunkX, chunkZ);
    }

    public static boolean regionHas(String dimension, int regionX, int regionZ) {
        return view.regionHas(dimension, regionX, regionZ);
    }

    public static int regionHash(String dimension, int regionX, int regionZ) {
        return view.regionHash(dimension, regionX, regionZ);
    }

    public static int generation() {
        return view.generation;
    }

    public static int parcelChunks(String dimension, UUID parcelId) {
        return view.parcelChunks(dimension, parcelId);
    }

    public record Entry(int chunkX, int chunkZ, UUID owner, String ownerName, String name, UUID parcelId) {
    }

    private static final class View {
        static final View EMPTY = new View(0, Map.of(), Map.of());

        private final int generation;
        private final Map<String, Map<Long, Entry>> chunks;
        private final Map<String, Map<Long, List<Entry>>> regions;

        private View(int generation, Map<String, Map<Long, Entry>> chunks, Map<String, Map<Long, List<Entry>>> regions) {
            this.generation = generation;
            this.chunks = chunks;
            this.regions = regions;
        }

        static View from(ClaimSnapshot snapshot) {
            Map<String, Map<Long, Entry>> chunks = new HashMap<>();
            Map<String, Map<Long, List<Entry>>> regions = new HashMap<>();
            for (ClaimSnapshot.PlacedClaim claim : snapshot.claims()) {
                Entry entry = new Entry(claim.chunkX(), claim.chunkZ(), claim.owner(), claim.ownerName(), claim.name(), claim.parcelId());
                chunks.computeIfAbsent(claim.dimension(), key -> new HashMap<>())
                        .put(key(claim.chunkX(), claim.chunkZ()), entry);
                int regionX = claim.chunkX() >> 5;
                int regionZ = claim.chunkZ() >> 5;
                regions.computeIfAbsent(claim.dimension(), key -> new HashMap<>())
                        .computeIfAbsent(key(regionX, regionZ), key -> new ArrayList<>())
                        .add(entry);
            }
            for (Map<Long, List<Entry>> byRegion : regions.values()) {
                for (List<Entry> entries : byRegion.values()) {
                    entries.sort(Comparator.comparingInt(Entry::chunkX).thenComparingInt(Entry::chunkZ));
                }
            }
            return new View(snapshot.generation(), chunks, regions);
        }

        Entry get(String dimension, int chunkX, int chunkZ) {
            Map<Long, Entry> inDimension = chunks.get(dimension);
            if (inDimension == null) {
                return null;
            }
            return inDimension.get(key(chunkX, chunkZ));
        }

        boolean regionHas(String dimension, int regionX, int regionZ) {
            Map<Long, List<Entry>> inDimension = regions.get(dimension);
            return inDimension != null && inDimension.containsKey(key(regionX, regionZ));
        }

        int regionHash(String dimension, int regionX, int regionZ) {
            int hash = 17;
            hash = 31 * hash + generation;
            hash = 31 * hash + dimension.hashCode();
            hash = 31 * hash + regionX;
            hash = 31 * hash + regionZ;
            Map<Long, List<Entry>> inDimension = regions.get(dimension);
            if (inDimension == null) {
                return hash;
            }
            List<Entry> entries = inDimension.get(key(regionX, regionZ));
            if (entries == null) {
                return hash;
            }
            for (Entry entry : entries) {
                hash = 31 * hash + entry.chunkX();
                hash = 31 * hash + entry.chunkZ();
                hash = 31 * hash + entry.owner().hashCode();
                hash = 31 * hash + entry.ownerName().hashCode();
                hash = 31 * hash + entry.name().hashCode();
                hash = 31 * hash + entry.parcelId().hashCode();
            }
            return hash;
        }

        int parcelChunks(String dimension, UUID parcelId) {
            if (parcelId == null) {
                return 0;
            }
            Map<Long, Entry> inDimension = chunks.get(dimension);
            if (inDimension == null) {
                return 0;
            }
            int count = 0;
            for (Entry entry : inDimension.values()) {
                if (parcelId.equals(entry.parcelId())) {
                    count++;
                }
            }
            return count;
        }

        private static long key(int x, int z) {
            return (((long) x) << 32) | (z & 0xFFFFFFFFL);
        }
    }
}
