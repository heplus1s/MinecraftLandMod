package dev.jdguo.land.claim;

import java.util.*;

/** Per-request spatial buckets: query work depends on nearby claims, not full-world scans per selection. */
public final class NearbyOwners {
    public record Owned(int x, int z, UUID owner) {}
    private record Cell(long x, long z) {}
    private NearbyOwners() {}
    public static int[] count(Collection<Owned> claims, int[] coordinates, int radius) { return new Index(claims, radius).count(coordinates); }
    public static final class Index {
        private final int radius;
        private final long side;
        private final Map<Cell, List<Owned>> buckets = new HashMap<>();
        public Index(Collection<Owned> claims, int radius) {
            if (radius < 0 || radius > 128) throw new IllegalArgumentException("radius");
            this.radius = radius; this.side = radius + 1L;
            for (Owned claim : claims) buckets.computeIfAbsent(new Cell(Math.floorDiv((long)claim.x(), side), Math.floorDiv((long)claim.z(), side)), ignored -> new ArrayList<>()).add(claim);
        }
        public int[] count(int[] coordinates) {
            int[] result = new int[checkedSize(coordinates)];
            for (int i = 0; i < result.length; i++) {
                Set<UUID> owners = new HashSet<>();
                visit(coordinates[i * 2], coordinates[i * 2 + 1], (claim, weight) -> owners.add(claim.owner()));
                result[i] = owners.size();
            }
            return result;
        }

        public long[] weightedChunks(int[] coordinates) {
            long[] result = new long[checkedSize(coordinates)];
            for (int i = 0; i < result.length; i++) {
                final int idx = i;
                visit(coordinates[i * 2], coordinates[i * 2 + 1], (claim, weight) -> result[idx] += weight);
            }
            return result;
        }

        private static int checkedSize(int[] coordinates) {
            if (coordinates == null || coordinates.length == 0 || coordinates.length % 2 != 0 || coordinates.length > 2048)
                throw new IllegalArgumentException("pricing selection");
            return coordinates.length / 2;
        }

        private void visit(long x, long z, java.util.function.ObjLongConsumer<Owned> consumer) {
            long cx = Math.floorDiv(x, side), cz = Math.floorDiv(z, side);
            for (long bx = cx - 1; bx <= cx + 1; bx++) for (long bz = cz - 1; bz <= cz + 1; bz++) {
                for (Owned claim : buckets.getOrDefault(new Cell(bx, bz), List.of())) {
                    long distance = Math.max(Math.abs(x - claim.x()), Math.abs(z - claim.z()));
                    if (distance <= radius) consumer.accept(claim, side - distance);
                }
            }
        }
    }
}
