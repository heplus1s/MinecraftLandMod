package dev.jdguo.land.client.xaero;

import dev.jdguo.land.client.ClientClaims;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Colors and tooltip text shared by the world map and minimap highlighters.
 * Owner UUID determines the shared stable color; the owner may customize it.
 * A border is drawn where the parcel changes, even when both sides have the same owner.
 */
public final class ClaimHighlighterLogic {
    public static final int SALE_RGB = 0xE0C040;

    private ClaimHighlighterLogic() {
    }

    public static boolean regionHas(RegistryKey<World> dimension, int regionX, int regionZ) {
        return dev.jdguo.land.client.ClaimDisplayPreferences.visible() && ClientClaims.regionHas(dev.jdguo.land.ui.ClientDisplayDimensions.resolve(dimension), regionX, regionZ);
    }

    public static boolean chunkClaimed(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return dev.jdguo.land.client.ClaimDisplayPreferences.visible() && ClientClaims.get(dev.jdguo.land.ui.ClientDisplayDimensions.resolve(dimension), chunkX, chunkZ) != null;
    }

    public static int regionHash(RegistryKey<World> dimension, int regionX, int regionZ) {
        return java.util.Objects.hash(ClientClaims.regionHash(dev.jdguo.land.ui.ClientDisplayDimensions.resolve(dimension), regionX, regionZ),
                dev.jdguo.land.client.ClientOwnerColors.revision(), dev.jdguo.land.client.ClaimDisplayPreferences.revision(), dev.jdguo.land.ui.ClientSales.revision());
    }

    /**
     * Fills the five color slots Xaero's {@code ChunkHighlighter} reads:
     * center, -Z, +X, +Z, -X. Returns false when the chunk is not claimed.
     */
    public static boolean fill(int[] store, RegistryKey<World> dimension, int chunkX, int chunkZ) {
        if (!dev.jdguo.land.client.ClaimDisplayPreferences.visible()) return false;
        String dim = dev.jdguo.land.ui.ClientDisplayDimensions.resolve(dimension);
        ClientClaims.Entry entry = ClientClaims.get(dim, chunkX, chunkZ);
        if (entry == null) {
            return false;
        }
        int rgb = dev.jdguo.land.client.ClientOwnerColors.rgb(entry.owner());
        int fill = dev.jdguo.land.color.OwnerColorPalette.xaeroArgb(rgb, 0x66);
        var listing = dev.jdguo.land.ui.ClientSales.find(dim, chunkX, chunkZ);
        UUID saleId = listing == null ? null : listing.id();
        boolean sale = saleId != null;
        int edge = dev.jdguo.land.color.OwnerColorPalette.xaeroArgb(sale ? SALE_RGB : rgb, 0xFF);
        store[0] = fill;
        store[1] = border(dim, chunkX, chunkZ - 1, entry.parcelId(), saleId) ? edge : fill;
        store[2] = border(dim, chunkX + 1, chunkZ, entry.parcelId(), saleId) ? edge : fill;
        store[3] = border(dim, chunkX, chunkZ + 1, entry.parcelId(), saleId) ? edge : fill;
        store[4] = border(dim, chunkX - 1, chunkZ, entry.parcelId(), saleId) ? edge : fill;
        return true;
    }

    public static Text tooltip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        if (!dev.jdguo.land.client.ClaimDisplayPreferences.visible()) return null;
        ClientClaims.Entry entry = ClientClaims.get(dev.jdguo.land.ui.ClientDisplayDimensions.resolve(dimension), chunkX, chunkZ);
        if (entry == null) {
            return null;
        }
        String owner = entry.ownerName().isBlank() ? "未知" : entry.ownerName();
        int chunks = ClientClaims.parcelChunks(dev.jdguo.land.ui.ClientDisplayDimensions.resolve(dimension), entry.parcelId());
        String size = Math.max(chunks, 1) + " 块";
        if (dev.jdguo.land.ui.ClientSales.forSale(dev.jdguo.land.ui.ClientDisplayDimensions.resolve(dimension), chunkX, chunkZ)) size += " · 出售中";
        if (entry.owner().equals(selfId())) size += " · 你的领地";
        if (entry.name().isBlank()) {
            String shortId = entry.parcelId().toString().replace("-", "").substring(0, 8);
            return Text.literal(owner + " 的领地 · " + size + " · " + shortId);
        }
        return Text.literal(entry.name() + "（" + owner + "） · " + size);
    }

    private static boolean border(String dimension, int chunkX, int chunkZ, UUID parcelId, UUID saleId) {
        ClientClaims.Entry neighbor = ClientClaims.get(dimension, chunkX, chunkZ);
        var neighborSale = dev.jdguo.land.ui.ClientSales.find(dimension, chunkX, chunkZ);
        return dev.jdguo.land.client.ClaimColorBoundary.isBoundary(parcelId, saleId,
                neighbor == null ? null : neighbor.parcelId(), neighborSale == null ? null : neighborSale.id());
    }

    private static UUID selfId() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }
        return client.player.getUuid();
    }
}
