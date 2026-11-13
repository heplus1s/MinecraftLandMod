package dev.jdguo.land.claim;

/**
 * Chunk-grid geometry. Parcels grow by whole chunks that share an edge.
 * Diagonal chunks are not adjacent, and nothing smaller than a chunk exists.
 */
public final class ParcelGeometry {
    private ParcelGeometry() {
    }

    public static boolean edgeAdjacent(int chunkX1, int chunkZ1, int chunkX2, int chunkZ2) {
        int dx = Math.abs(chunkX1 - chunkX2);
        int dz = Math.abs(chunkZ1 - chunkZ2);
        return dx + dz == 1;
    }

    public static long pack(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
