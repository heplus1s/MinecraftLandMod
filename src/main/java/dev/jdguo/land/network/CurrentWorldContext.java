package dev.jdguo.land.network;

import dev.jdguo.land.message.LandMessages;
import dev.jdguo.land.trade.LandTrade;
import dev.jdguo.land.trade.TradeRequest;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;
import java.util.*;

/** Optional handshake for proxy-rewritten client dimensions. Legacy packets are unchanged. */
public final class CurrentWorldContext {
    public static final Identifier REQUEST = new Identifier("landmod", "world_context_request");
    public static final Identifier RESPONSE = new Identifier("landmod", "world_context_response");
    public static final Identifier TRADE = new Identifier("landmod", "context_trade");
    private static final Map<UUID, WorldContextLease> LEASES = new HashMap<>();
    private CurrentWorldContext() {}
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LEASES.remove(handler.player.getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> LEASES.remove(player.getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> LEASES.remove(newPlayer.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LEASES.clear());
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, sender) -> {
            final String nonce;
            try { nonce = buf.readString(64); } catch (RuntimeException invalid) { return; }
            server.execute(() -> {
                if (!RequestAccess.current(server.getPlayerManager().getPlayer(player.getUuid()), player, player.networkHandler, handler)
                        || !ServerPlayNetworking.canSend(player, RESPONSE)) return;
                var lease = LEASES.computeIfAbsent(player.getUuid(), ignored -> new WorldContextLease());
                String token = lease.valid(handler, player.getWorld(), lease.token(), System.nanoTime())
                        ? lease.token() : UUID.randomUUID().toString();
                String dimension = player.getWorld().getRegistryKey().getValue().toString();
                lease.bind(handler, player.getWorld(), token, dimension, System.nanoTime());
                var out = PacketByteBufs.create();
                out.writeString(nonce, 64); out.writeString(token, 64); out.writeString(dimension, 256);
                ServerPlayNetworking.send(player, RESPONSE, out);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(TRADE, (server, player, handler, buf, sender) -> {
            final String token; final TradeRequest request;
            try { token = buf.readString(64); request = TradeRequest.read(buf); }
            catch (RuntimeException invalid) { return; }
            server.execute(() -> {
                if (!RequestAccess.current(server.getPlayerManager().getPlayer(player.getUuid()), player, player.networkHandler, handler)) return;
                var lease = LEASES.get(player.getUuid());
                if (lease == null || !lease.valid(handler, player.getWorld(), token, System.nanoTime())
                        || request.chunks().stream().anyMatch(key -> !key.dimension().equals(lease.dimension()))) {
                    player.sendMessage(LandMessages.chat("世界连接已变化或确认已过期，请重新选择地块。"), false);
                    return;
                }
                // All ownership, sale, price, permission and dimension checks still run in the original path.
                LandTrade.apply(player, request);
            });
        });
    }
}
