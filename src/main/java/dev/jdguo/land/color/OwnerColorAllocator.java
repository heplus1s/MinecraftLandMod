package dev.jdguo.land.color;

import java.util.*;

/** First assignment only. Existing assignments are never recolored when neighbors change. */
public final class OwnerColorAllocator {
    public record Cell(String dimension, int x, int z, UUID owner) { }
    private record Position(String dimension, int x, int z) { }
    private OwnerColorAllocator() { }
    public static Map<UUID, Integer> allocate(Collection<Cell> claims, Map<UUID, Integer> assigned, Map<UUID, Integer> custom) {
        Map<Position, UUID> positions = new HashMap<>();
        SortedSet<UUID> missing = new TreeSet<>();
        for (Cell cell : claims) {
            positions.put(new Position(cell.dimension(), cell.x(), cell.z()), cell.owner());
            if (!assigned.containsKey(cell.owner())) missing.add(cell.owner());
        }
        Map<UUID, Set<UUID>> neighbors = new HashMap<>();
        for (Cell cell : claims) {
            if (!missing.contains(cell.owner())) continue;
            var touching = neighbors.computeIfAbsent(cell.owner(), ignored -> new TreeSet<>());
            add(touching, positions, cell, (long)cell.x()-1, cell.z());
            add(touching, positions, cell, (long)cell.x()+1, cell.z());
            add(touching, positions, cell, cell.x(), (long)cell.z()-1);
            add(touching, positions, cell, cell.x(), (long)cell.z()+1);
        }
        Map<UUID,Integer> all = new HashMap<>(assigned), added = new LinkedHashMap<>();
        for (UUID owner : missing) {
            List<Integer> adjacent = new ArrayList<>();
            for (UUID neighbor : neighbors.getOrDefault(owner, Set.of())) {
                Integer color = custom.getOrDefault(neighbor, all.get(neighbor));
                if (color != null) adjacent.add(color);
            }
            int rgb = choose(owner, adjacent);
            all.put(owner, rgb); added.put(owner, rgb);
        }
        return added;
    }
    private static void add(Set<UUID> neighbors, Map<Position,UUID> positions, Cell cell, long x, long z) {
        if (x<Integer.MIN_VALUE||x>Integer.MAX_VALUE||z<Integer.MIN_VALUE||z>Integer.MAX_VALUE) return;
        UUID other=positions.get(new Position(cell.dimension(), (int)x,(int)z));
        if(other!=null&&!other.equals(cell.owner()))neighbors.add(other);
    }
    public static int choose(UUID owner, Collection<Integer> neighboringColors) {
        int best = OwnerColorPalette.color(owner);
        if (neighboringColors.isEmpty()) return best;
        int score = minimumDistance(best, neighboringColors);
        // Stable UUID seed plus 137.5 degree (golden-angle) steps across 24 candidates.
        int seed = Math.floorMod(owner.hashCode(),1536);
        for(int i=1;i<24;i++) {
            int candidate=OwnerColorPalette.hueRgb(seed+(int)Math.round(i*1536*0.3819660112501051));
            int distance=minimumDistance(candidate,neighboringColors);
            if(distance>score){best=candidate;score=distance;}
        }
        return best;
    }
    private static int minimumDistance(int rgb,Collection<Integer> colors){
        int min=Integer.MAX_VALUE;for(int other:colors)min=Math.min(min,distanceSquared(rgb,other));return min;
    }
    public static int distanceSquared(int a,int b){
        int r=(a>>>16&255)-(b>>>16&255),g=(a>>>8&255)-(b>>>8&255),bl=(a&255)-(b&255);
        return r*r+g*g+bl*bl;
    }
}
