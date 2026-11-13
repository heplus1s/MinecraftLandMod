package dev.jdguo.land.client.xaero;

import dev.jdguo.land.client.ClientClaims;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Colors and tooltip text shared by the world map and minimap highlighters.
 * Your own claims are green. Other players' claims are orange.
 */
public final class ClaimHighlighterLogic {
    public static final int OWN_FILL = 0x6635D06A;
    public static final int OWN_EDGE = 0xFF35D06A;
    public static final int OTHER_FILL = 0x66E08A2E;
    public static final int OTHER_EDGE = 0xFFE08A2E;

    private ClaimHighlighterLogic() {
    }

    public static boolean regionHas(RegistryKey<World> dimension, int regionX, int regionZ) {
        return ClientClaims.regionHas(dimension.getValue().toString(), regionX, regionZ);
    }

    public static boolean chunkClaimed(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return ClientClaims.get(dimension.getValue().toString(), chunkX, chunkZ) != null;
    }

    public static int regionHash(RegistryKey<World> dimension, int regionX, int regionZ) {
        return ClientClaims.regionHash(dimension.getValue().toString(), regionX, regionZ);
    }

    /**
     * Fills the five color slots Xaero's {@code ChunkHighlighter} reads:
     * center, -Z, +X, +Z, -X. Returns false when the chunk is not claimed.
     */
    public static boolean fill(int[] store, RegistryKey<World> dimension, int chunkX, int chunkZ) {
        String dim = dimension.getValue().toString();
        ClientClaims.Entry entry = ClientClaims.get(dim, chunkX, chunkZ);
        if (entry == null) {
            return false;
        }
        UUID self = selfId();
        boolean own = self != null && self.equals(entry.owner());
        int fill = own ? OWN_FILL : OTHER_FILL;
        int edge = own ? OWN_EDGE : OTHER_EDGE;
        store[0] = fill;
        store[1] = border(dim, chunkX, chunkZ - 1, entry.owner()) ? edge : fill;
        store[2] = border(dim, chunkX + 1, chunkZ, entry.owner()) ? edge : fill;
        store[3] = border(dim, chunkX, chunkZ + 1, entry.owner()) ? edge : fill;
        store[4] = border(dim, chunkX - 1, chunkZ, entry.owner()) ? edge : fill;
        return true;
    }

    public static Text tooltip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        ClientClaims.Entry entry = ClientClaims.get(dimension.getValue().toString(), chunkX, chunkZ);
        if (entry == null) {
            return null;
        }
        String owner = entry.ownerName().isBlank() ? "未知" : entry.ownerName();
        if (entry.name().isBlank()) {
            return Text.literal(owner + " 的领地");
        }
        return Text.literal(entry.name() + "（" + owner + "）");
    }

    private static boolean border(String dimension, int chunkX, int chunkZ, UUID owner) {
        ClientClaims.Entry neighbor = ClientClaims.get(dimension, chunkX, chunkZ);
        return neighbor == null || !neighbor.owner().equals(owner);
    }

    private static UUID selfId() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }
        return client.player.getUuid();
    }
}
