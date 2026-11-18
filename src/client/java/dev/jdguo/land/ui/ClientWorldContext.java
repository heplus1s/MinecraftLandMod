package dev.jdguo.land.ui;

import dev.jdguo.land.network.CurrentWorldContext;
import dev.jdguo.land.network.WorldContextLease;
import dev.jdguo.land.trade.TradeRequest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import java.util.UUID;

/** Never reuse a binding after a reconnect, respawn, dimension or proxy world switch. */
public final class ClientWorldContext {
    private static final WorldContextLease LEASE = new WorldContextLease();
    private static Object connection, world, player, pendingConnection, pendingWorld, pendingPlayer;
    private static String nonce;
    private static int age;
    private static long pendingDeadline;
    private ClientWorldContext() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CurrentWorldContext.RESPONSE, (client, handler, buf, sender) -> {
            final String receivedNonce, token, dimension;
            try { receivedNonce = buf.readString(64); token = buf.readString(64); dimension = buf.readString(256); }
            catch (RuntimeException invalid) { return; }
            client.execute(() -> {
                if (System.nanoTime() >= pendingDeadline || pendingPlayer != client.player || client.player == null || client.getNetworkHandler() != handler || !WorldContextLease.acceptsResponse(
                        nonce, receivedNonce, pendingConnection, handler, pendingWorld, client.world)) return;
                if (net.minecraft.util.Identifier.tryParse(dimension) == null || token.isBlank()) return;
                if (!token.equals(LEASE.token()) || !dimension.equals(LEASE.dimension())) {
                    LandPick.clear(); LandQuotes.reset();
                }
                LEASE.bind(handler, client.world, token, dimension, System.nanoTime());
                nonce = null;
            });
        });
    }
    public static void reset() {
        LEASE.clear(); connection = world = player = pendingConnection = pendingWorld = pendingPlayer = null; nonce = null; age = 0; pendingDeadline = 0;
    }
    public static void tick(MinecraftClient client) {
        if (connection != client.getNetworkHandler() || world != client.world || player != client.player) {
            reset(); connection = client.getNetworkHandler(); world = client.world; player = client.player;
            LandPick.clear(); LandQuotes.reset();
        }
        if (client.world == null || client.getNetworkHandler() == null) return;
        if (age++ % 40 != 0 || !supported()) return;
        long now = System.nanoTime();
        if (!WorldContextLease.mayStartRequest(nonce != null, now, pendingDeadline)) return;
        pendingDeadline = now + 8_000_000_000L;
        nonce = UUID.randomUUID().toString(); pendingConnection = connection; pendingWorld = world; pendingPlayer = player;
        var out = PacketByteBufs.create(); out.writeString(nonce, 64);
        ClientPlayNetworking.send(CurrentWorldContext.REQUEST, out);
    }
    public static boolean supported() {
        return ClientPlayNetworking.canSend(CurrentWorldContext.REQUEST) && ClientPlayNetworking.canSend(CurrentWorldContext.TRADE);
    }
    public static boolean ready(MinecraftClient client) {
        return player != null && player == client.player && LEASE.valid(client.getNetworkHandler(), client.world, LEASE.token(), System.nanoTime());
    }
    public static String currentDimension(MinecraftClient client) {
        if (client.world == null) return null;
        if (supported()) return ready(client) ? LEASE.dimension() : null;
        return client.world.getRegistryKey().getValue().toString();
    }
    public static boolean sendCurrentTrade(MinecraftClient client, TradeRequest request) {
        if (!supported() || !ready(client)) { LandQuotes.notice("正在确认当前服务器世界，请稍后重新选择地块。"); return false; }
        var out = PacketByteBufs.create(); out.writeString(LEASE.token(), 64); request.write(out);
        ClientPlayNetworking.send(CurrentWorldContext.TRADE, out); return true;
    }
}
