package dev.jdguo.land.integration;

import dev.jdguo.land.claim.ClaimKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.List;
import java.util.UUID;

/** Optional server-only bridge. A missing/old market must never fall back to free acquisition. */
public final class MarketTradeBridge {
    private MarketTradeBridge() {}
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
