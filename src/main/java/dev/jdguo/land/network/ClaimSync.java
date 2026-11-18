package dev.jdguo.land.network;

import dev.jdguo.land.world.ClaimSavedData;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ClaimSync {
    public static final Identifier CHANNEL = new Identifier("landmod", "claims");

    private ClaimSync() {
    }

    public static void register() {
        // A JOIN-time send can precede Fabric's client channel advertisement.
        // This optional request is sent only by clients that see our channel.
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, response) -> {
            final int version;
            try { version = buf.readVarInt(); } catch (RuntimeException malformed) { return; }
            if (version != 1) return;
            server.execute(() -> {
                if (server.getPlayerManager().getPlayer(player.getUuid()) != player) return;
                send(player, ClaimSavedData.get(server).claims().toSnapshot());
                dev.jdguo.land.trade.LandTrade.send(player, server);
            });
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            ClaimSavedData data = ClaimSavedData.get(server);
            boolean renamed = data.claims().updateName(player.getUuid(), player.getGameProfile().getName());
            if (renamed) {
                data.markDirty();
                broadcast(server);
            } else {
                send(player, data.claims().toSnapshot());
            }
        });
    }

    public static void broadcast(MinecraftServer server) {
        ClaimSnapshot snapshot = ClaimSavedData.get(server).claims().toSnapshot();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            send(player, snapshot);
        }
    }

    public static void send(ServerPlayerEntity player, ClaimSnapshot snapshot) {
        ColorSync.send(player);
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        snapshot.write(buf);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
}
