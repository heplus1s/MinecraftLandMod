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
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        snapshot.write(buf);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
}
