package dev.jdguo.land.network;

import dev.jdguo.land.world.OwnerColorSavedData;
import dev.jdguo.land.message.LandMessages;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.*;

/** Optional versioned cosmetic packets, separate from the legacy claim snapshot. */
public final class ColorSync {
    public static final Identifier CHANNEL = new Identifier("landmod", "owner_colors");
    public static final int VERSION = 2, PAGE_SIZE = 256;
    private record Sent(Object handler, OwnerColorSavedData data, long revision) { }
    private static final Map<UUID, OwnerColorSessionBudget> SESSION_BUDGETS = new HashMap<>();
    private static final Map<UUID, OwnerColorSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, Sent> SENT = new HashMap<>();
    private static final Map<UUID, Long> REQUEST_TIME = new HashMap<>(), EDIT_TIME = new HashMap<>();
    private ColorSync() { }
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid(); SESSION_BUDGETS.remove(id); SESSIONS.remove(id); SENT.remove(id); REQUEST_TIME.remove(id); EDIT_TIME.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SESSION_BUDGETS.clear(); SESSIONS.clear(); SENT.clear(); REQUEST_TIME.clear(); EDIT_TIME.clear(); });
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, sender) -> {
            final OwnerColorRequest request;
            try { request = OwnerColorRequest.read(buf); } catch (RuntimeException malformed) { return; }
            server.execute(() -> {
                if (!RequestAccess.current(server.getPlayerManager().getPlayer(player.getUuid()), player,
                        player.networkHandler, handler)) return;
                UUID owner = player.getUuid();
                if (request.operation() == 0) {
                    var prior = SESSIONS.get(owner);
                    if (prior == null || !prior.matches(handler, player.getWorld(), request.session())) {
                        // Nonce changes may bypass the old request timer, never the connection's burst budget.
                        if (!SESSION_BUDGETS.computeIfAbsent(owner, ignored -> new OwnerColorSessionBudget())
                                .allow(handler, System.nanoTime())) return;
                        // A new client world/connection epoch must not be blocked by the old request timer.
                        SESSIONS.put(owner, new OwnerColorSession(handler, player.getWorld(), request.session()));
                        SENT.remove(owner); REQUEST_TIME.remove(owner); EDIT_TIME.remove(owner);
                    }
                    if (!allow(REQUEST_TIME, owner, 2_000_000_000L)) return;
                    SENT.remove(owner); send(player); return;
                }
                var subscription = SESSIONS.get(owner);
                if (subscription == null || !subscription.matches(handler, player.getWorld(), request.session())) return;
                if (!allow(EDIT_TIME, owner, 500_000_000L)) return;
                var data = OwnerColorSavedData.get(server);
                data.ensureClaims(server);
                long before = data.revision();
                final boolean changed;
                try { changed = request.operation() == 1 ? data.set(owner, request.rgb()) : data.reset(owner); }
                catch (IllegalStateException full) {
                    player.sendMessage(LandMessages.chat("自定义颜色数量已达上限，可恢复自动颜色。"), false); return;
                }
                if (changed) broadcastChange(server, data, before, owner, data.color(owner));
                else { SENT.remove(owner); send(player); }
                player.sendMessage(LandMessages.chat(request.operation() == 1 ? "已保存你的领地颜色。" : "已恢复你的自动领地颜色。"), false);
            });
        });
    }
    private static boolean allow(Map<UUID, Long> times, UUID owner, long interval) {
        long now = System.nanoTime(); Long last = times.get(owner);
        if (last != null && now - last < interval) return false;
        times.put(owner, now); return true;
    }
    /** Called after claim synchronization; revision cache avoids resending unchanged tables. */
    public static void send(ServerPlayerEntity player) {
        if (player.getServer() == null || !ServerPlayNetworking.canSend(player, CHANNEL)) return;
        var subscription = SESSIONS.get(player.getUuid());
        if (subscription == null || !subscription.matches(player.networkHandler, player.getWorld(), subscription.session())) return;
        var data = OwnerColorSavedData.get(player.getServer());
        data.ensureClaims(player.getServer());
        Sent last = SENT.get(player.getUuid());
        if (last != null && last.handler() == player.networkHandler && last.data() == data && last.revision() == data.revision()) return;
        var entries = new ArrayList<>(data.effective().entrySet());
        UUID transferId = UUID.randomUUID();
        int pageCount = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        for (int offset = 0; offset < entries.size() || offset == 0; offset += PAGE_SIZE) {
            var page = entries.subList(offset, Math.min(offset + PAGE_SIZE, entries.size()));
            packet(player, subscription.session(), true, transferId, offset / PAGE_SIZE, pageCount, page);
        }
        SENT.put(player.getUuid(), new Sent(player.networkHandler, data, data.revision()));
    }
    private static void broadcastChange(MinecraftServer server, OwnerColorSavedData data, long before, UUID owner, int rgb) {
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(player, CHANNEL)) continue;
            var subscription = SESSIONS.get(player.getUuid());
            if (subscription == null || !subscription.matches(player.networkHandler, player.getWorld(), subscription.session())) continue;
            Sent last = SENT.get(player.getUuid());
            if (last == null || last.handler() != player.networkHandler || last.data() != data || last.revision() != before) {
                send(player); continue;
            }
            packet(player, subscription.session(), false, UUID.randomUUID(), 0, 1, List.of(Map.entry(owner, rgb)));
            SENT.put(player.getUuid(), new Sent(player.networkHandler, data, data.revision()));
        }
    }
    private static void packet(ServerPlayerEntity player, UUID session, boolean full, UUID transferId, int pageIndex, int pageCount, List<Map.Entry<UUID, Integer>> entries) {
        if (entries.size() > PAGE_SIZE) throw new IllegalArgumentException("page size");
        var buf = PacketByteBufs.create(); buf.writeVarInt(VERSION); buf.writeUuid(session); buf.writeBoolean(full); buf.writeUuid(transferId);
        buf.writeVarInt(pageIndex); buf.writeVarInt(pageCount); buf.writeVarInt(entries.size());
        for (var entry : entries) { buf.writeUuid(entry.getKey()); buf.writeInt(entry.getValue()); }
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
}
