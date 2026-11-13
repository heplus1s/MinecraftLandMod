package dev.jdguo.land.trade;

import dev.jdguo.land.LandMod;
import dev.jdguo.land.network.SaleSnapshot;
import dev.jdguo.land.world.ClaimSavedData;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Optional trade channel. The server always understands it. A client sends a
 * request only when the land UI pack is installed, so joining stays vanilla.
 */
public final class LandTrade {
    public static final Identifier CHANNEL = new Identifier("landmod", "trade");
    public static final Identifier SALES = new Identifier("landmod", "sales");

    private LandTrade() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, responseSender) -> {
            TradeRequest request;
            try {
                request = TradeRequest.read(buf);
            } catch (RuntimeException exception) {
                LandMod.LOGGER.warn("Ignored a land trade packet", exception);
                return;
            }
            server.execute(() -> apply(player, request));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, server));
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            send(player, server);
        }
    }

    public static void send(ServerPlayerEntity player, MinecraftServer server) {
        if (!ServerPlayNetworking.canSend(player, SALES)) {
            return;
        }
        ClaimSavedData data = ClaimSavedData.get(server);
        int before = data.claims().saleRevision();
        SaleSnapshot snapshot = new SaleSnapshot(data.claims().saleRevision(), data.claims().sales());
        if (data.claims().saleRevision() != before) {
            data.markDirty();
        }
        PacketByteBuf buf = PacketByteBufs.create();
        snapshot.write(buf);
        ServerPlayNetworking.send(player, SALES, buf);
    }

    public static void apply(ServerPlayerEntity player, TradeRequest request) {
        ClaimSavedData data = ClaimSavedData.get(player.getServer());
        switch (request.action()) {
            case BUY_PARCEL -> {
                if (request.parcelId() == null) {
                    player.sendMessage(dev.jdguo.land.message.LandMessages.chat("没有这块宗地。"), false);
                    return;
                }
                data.buyParcel(player, request.parcelId());
            }
            case BUY_CHUNKS -> data.buyChunks(player, request.chunks());
            case SELL_PARCEL -> {
                if (request.parcelId() == null) {
                    player.sendMessage(dev.jdguo.land.message.LandMessages.chat("没有这块宗地。"), false);
                    return;
                }
                data.sellParcel(player, request.parcelId());
            }
            case SELL_CHUNKS -> data.sellChunks(player, request.chunks());
            case CANCEL -> {
                if (request.parcelId() == null) {
                    player.sendMessage(dev.jdguo.land.message.LandMessages.chat("没有这块宗地。"), false);
                    return;
                }
                data.cancelSale(player, request.parcelId());
            }
        }
    }
}
