package dev.jdguo.land.ui;

import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.client.ClientClaims;
import dev.jdguo.land.message.LandMessages;
import dev.jdguo.land.trade.LandTrade;
import dev.jdguo.land.trade.TradeRequest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

import java.util.List;

/**
 * Sends a purchase or listing to the server. The server answers in {@code [领地]} chat.
 */
public final class LandTradeClient {
    private LandTradeClient() {
    }

    public static void buy(MinecraftClient client) {
        List<ClaimKey> keys = LandPick.selected();
        if (keys.isEmpty()) {
            tell(client, "还没有选中区块。");
            return;
        }
        if (!validDimensions(client, keys)) return;
        if (coversParcel(keys)) {
            send(TradeRequest.buyParcel(ClientClaims.get(keys.get(0).dimension(), keys.get(0).chunkX(), keys.get(0).chunkZ()).parcelId()));
            return;
        }
        send(TradeRequest.buyChunks(keys));
    }

    public static void sell(MinecraftClient client) {
        List<ClaimKey> keys = LandPick.selected();
        if (keys.isEmpty()) {
            tell(client, "还没有选中区块。");
            return;
        }
        if (!validDimensions(client, keys)) return;
        if (coversParcel(keys)) {
            send(TradeRequest.sellParcel(ClientClaims.get(keys.get(0).dimension(), keys.get(0).chunkX(), keys.get(0).chunkZ()).parcelId()));
            return;
        }
        send(TradeRequest.sellChunks(keys));
    }

    public static void cancel(MinecraftClient client) {
        List<ClaimKey> keys = LandPick.selected();
        if (keys.isEmpty()) {
            tell(client, "还没有选中区块。");
            return;
        }
        ClientClaims.Entry entry = ClientClaims.get(keys.get(0).dimension(), keys.get(0).chunkX(), keys.get(0).chunkZ());
        if (entry == null) {
            tell(client, "此处没有领地。");
            return;
        }
        send(TradeRequest.cancel(entry.parcelId()));
    }

    private static boolean validDimensions(MinecraftClient client, List<ClaimKey> keys) {
        var network = client.getNetworkHandler();
        for (var key : keys) {
            if (network == null || network.getWorldKeys().stream().noneMatch(world -> world.getValue().toString().equals(key.dimension()))) {
                tell(client, LandTradeFeedback.unknownDimension(key.dimension())); return false;
            }
        }
        return true;
    }

    private static boolean coversParcel(List<ClaimKey> keys) {
        String dimension = keys.get(0).dimension();
        ClientClaims.Entry first = ClientClaims.get(dimension, keys.get(0).chunkX(), keys.get(0).chunkZ());
        if (first == null) {
            return false;
        }
        List<ClientClaims.Entry> parcel = ClientClaims.parcelEntries(dimension, first.parcelId());
        if (parcel.size() != keys.size()) {
            return false;
        }
        for (ClientClaims.Entry entry : parcel) {
            if (!keys.contains(new ClaimKey(dimension, entry.chunkX(), entry.chunkZ()))) {
                return false;
            }
        }
        for (ClaimKey key : keys) {
            ClientClaims.Entry entry = ClientClaims.get(key.dimension(), key.chunkX(), key.chunkZ());
            if (entry == null || !entry.parcelId().equals(first.parcelId()) || !key.dimension().equals(dimension)) {
                return false;
            }
        }
        return true;
    }

    private static void send(TradeRequest request) {
        if (request.chunks().size() > TradeRequest.MAX_CHUNKS) {
            tell(MinecraftClient.getInstance(), "单次区块选区最多 " + TradeRequest.MAX_CHUNKS + " 块，请缩小范围或使用整宗地模式。");
            return;
        }
        if (!ClientPlayNetworking.canSend(LandTrade.CHANNEL)) {
            tell(MinecraftClient.getInstance(), "当前服务器不支持领地交易界面，请使用服务器提供的基础入口。");
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        request.write(buf);
        ClientPlayNetworking.send(LandTrade.CHANNEL, buf);
    }

    private static void tell(MinecraftClient client, String body) {
        LandQuotes.notice(body);
        if (client.player != null) {
            client.player.sendMessage(LandMessages.chat(body), false);
        }
    }
}
