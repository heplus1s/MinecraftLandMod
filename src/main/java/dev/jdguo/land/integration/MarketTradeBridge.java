package dev.jdguo.land.integration;

import dev.jdguo.land.claim.ClaimKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.List;
import java.util.UUID;

/** Optional server-only bridge. A missing/old market must never fall back to free acquisition. */
public final class MarketTradeBridge {
    private MarketTradeBridge() {}
    public static boolean initialEnabled(net.minecraft.server.MinecraftServer server) {
        try {
            return (Boolean) Class.forName("dev.jdguo.market.api.LandMarketTerminal")
                    .getMethod("initialEnabled", net.minecraft.server.MinecraftServer.class).invoke(null, server);
        } catch (ClassNotFoundException | NoSuchMethodException legacy) {
            return false; // No market policy exists on a legacy/no-market server.
        } catch (ReflectiveOperationException | LinkageError failure) {
            return true; // A failed policy read must not turn a paid claim into a free one.
        }
    }

    public static String governmentAllocationCheck(net.minecraft.server.MinecraftServer server, String dimension, int[] coordinates) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("marketmod")) return "ok";
        try {
            return (String) Class.forName("dev.jdguo.market.api.LandMarketTerminal")
                    .getMethod("governmentAllocationCheck", net.minecraft.server.MinecraftServer.class, String.class, int[].class)
                    .invoke(null, server, dimension, coordinates);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            return "请更新市场模组后再认购，避免占用正在结算的地块。";
        }
    }

    public static boolean openGovernmentListing(ServerPlayerEntity player, UUID parcelId) {
        try {
            Class.forName("dev.jdguo.market.api.GovernmentSale")
                    .getMethod("open", ServerPlayerEntity.class, UUID.class).invoke(null, player, parcelId);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            return false;
        }
    }

    public static void open(ServerPlayerEntity player, UUID parcel, String action, List<ClaimKey> keys) {
        int[] coordinates = new int[keys.size() * 2];
        for (int i = 0; i < keys.size(); i++) {
            coordinates[i * 2] = keys.get(i).chunkX();
            coordinates[i * 2 + 1] = keys.get(i).chunkZ();
        }
        String dimension = keys.isEmpty() ? "" : keys.get(0).dimension();
        try {
            Class.forName("dev.jdguo.market.api.LandMarketTerminal")
                    .getMethod("open", ServerPlayerEntity.class, UUID.class, String.class, String.class, int[].class)
                    .invoke(null, player, parcel, action, dimension, coordinates);
        } catch (ReflectiveOperationException | LinkageError exception) {
            player.sendMessage(Text.literal("土地买卖需要兼容的市场模组，请更新市场模组后重试。未扣款，也未转移领地。"), false);
        }
    }
}
