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
    private static boolean quoteTransport;
    private static long pendingDeadline;
    private ClientWorldContext() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CurrentWorldContext.RESPONSE, (client, handler, buf, sender) -> {
            final String receivedNonce, token, dimension;
            try { receivedNonce = buf.readString(64); token = buf.readString(64); dimension = buf.readString(256); }
            catch (RuntimeException invalid) { return; }
            client.execute(() -> {
                accept(client, handler, receivedNonce, token, dimension, false);
            });
        });
    }
    public static void acceptQuote(MinecraftClient client, Object handler, com.google.gson.JsonObject value) {
        if (!"trade-envelope-v1".equals(value.get("transport").getAsString())) return;
        accept(client, handler, value.get("nonce").getAsString(), value.get("token").getAsString(),
                value.get("dimension").getAsString(), true);
    }
    private static void accept(MinecraftClient client, Object handler, String receivedNonce, String token,
                               String dimension, boolean throughQuote) {
        if (System.nanoTime() >= pendingDeadline || pendingPlayer != client.player || client.player == null
                || client.getNetworkHandler() != handler || !WorldContextLease.acceptsResponse(
                nonce, receivedNonce, pendingConnection, handler, pendingWorld, client.world)) return;
        if (net.minecraft.util.Identifier.tryParse(dimension) == null || token.isBlank() || token.length() > 64) return;
        if (!token.equals(LEASE.token()) || !dimension.equals(LEASE.dimension())) {
            LandPick.clear(); LandQuotes.reset();
        }
        LEASE.bind(handler, client.world, token, dimension, System.nanoTime());
        quoteTransport = throughQuote;
        nonce = null;
    }
    public static void reset() {
        LEASE.clear(); connection = world = player = pendingConnection = pendingWorld = pendingPlayer = null; nonce = null; age = 0; pendingDeadline = 0; quoteTransport = false;
    }
    public static void tick(MinecraftClient client) {
        if (connection != client.getNetworkHandler() || world != client.world || player != client.player) {
            reset(); connection = client.getNetworkHandler(); world = client.world; player = client.player;
            LandPick.clear(); LandQuotes.reset();
        }
        if (client.world == null || client.getNetworkHandler() == null) return;
        boolean quoteAvailable = ClientPlayNetworking.canSend(dev.jdguo.land.trade.LandQuoteChannel.REQUEST);
        if (age++ % 40 != 0 || (!nativeSupported() && !quoteAvailable)) return;
        long now = System.nanoTime();
        if (!WorldContextLease.mayStartRequest(nonce != null, now, pendingDeadline)) return;
        pendingDeadline = now + 8_000_000_000L;
        nonce = UUID.randomUUID().toString(); pendingConnection = connection; pendingWorld = world; pendingPlayer = player;
        if (quoteAvailable) {
            var input = new com.google.gson.JsonObject(); input.addProperty("nonce", nonce);
            LandQuotes.send(CurrentWorldContext.QUOTE_OPERATION, input);
        }
        // Also probe the dedicated channel for compatibility with v1.0.10 servers.
        if (nativeSupported()) {
            var out = PacketByteBufs.create(); out.writeString(nonce, 64);
            ClientPlayNetworking.send(CurrentWorldContext.REQUEST, out);
        }
    }
    public static boolean supported() { return quoteTransport || nativeSupported(); }
    private static boolean nativeSupported() {
        return ClientPlayNetworking.canSend(CurrentWorldContext.REQUEST) && ClientPlayNetworking.canSend(CurrentWorldContext.TRADE);
    }
    public static String diagnostic(MinecraftClient client) {
        return "世界确认=" + (ready(client) ? LEASE.dimension() : "未完成")
                + "，通道=" + (quoteTransport ? "兼容报价" : nativeSupported() ? "专用" : "未发现专用通道")
                + "，报价通道=" + ClientPlayNetworking.canSend(dev.jdguo.land.trade.LandQuoteChannel.REQUEST)
                + "，请求=" + (nonce == null ? "无待答" : "等待应答");
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
        var out = PacketByteBufs.create();
        if (quoteTransport) {
            if (!ClientPlayNetworking.canSend(dev.jdguo.land.trade.LandTrade.CHANNEL)) return false;
            dev.jdguo.land.network.ContextTradeEnvelope.write(out, LEASE.token(), request);
            ClientPlayNetworking.send(dev.jdguo.land.trade.LandTrade.CHANNEL, out);
        } else {
            out.writeString(LEASE.token(), 64); request.write(out);
            ClientPlayNetworking.send(CurrentWorldContext.TRADE, out);
        }
        return true;
    }
}
