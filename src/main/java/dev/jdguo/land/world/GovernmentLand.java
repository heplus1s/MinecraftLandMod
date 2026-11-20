package dev.jdguo.land.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jdguo.land.api.LandApi;
import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.GovernmentOwner;
import dev.jdguo.land.integration.MarketTradeBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public final class GovernmentLand {
    private static final long CONFIRM_NANOS = 90_000_000_000L;
    private static final int MAX_PREVIEWS = 64;
    private static final Map<MinecraftServer, LinkedHashMap<UUID, Preview>> PREVIEWS = new WeakHashMap<>();
    private record Preview(java.lang.ref.WeakReference<ServerPlayerEntity> operator, String dimension, List<ClaimKey> keys, UUID token, long created) {
        boolean expired(long now) { return now - created >= CONFIRM_NANOS; }
    }
    private GovernmentLand() {}

    public static String request(MinecraftServer server, ServerPlayerEntity player, String payload) {
        if (!server.isOnThread() || !player.hasPermissionLevel(2)
                || server.getPlayerManager().getPlayer(player.getUuid()) != player)
            return reply(false, "只有管理员可以代管服务器用地。").toString();

        var input = JsonParser.parseString(payload).getAsJsonObject();
        String action = input.get("action").getAsString();
        var saved = ClaimSavedData.get(server);
        return switch (action) {
            case "preview" -> preview(server, player, saved, input).toString();
            case "confirm" -> confirm(server, player, saved, UUID.fromString(input.get("token").getAsString())).toString();
            case "list", "rename" -> {
                int page = integer(input, "page");
                String message = "服务器用地";
                if ("rename".equals(action)) {
                    var result = saved.renameGovernment(player, UUID.fromString(input.get("parcel").getAsString()), input.get("name").getAsString());
                    if (!result.succeeded()) yield reply(false, "无法改名：请检查名称及服务器宗地。").toString();
                    message = "宗地名称已更新。";
                }
                yield list(saved, page, message).toString();
            }
            default -> reply(false, "不支持的服务器用地操作。").toString();
        };
    }

    private static JsonObject preview(MinecraftServer server, ServerPlayerEntity player, ClaimSavedData saved, JsonObject input) {
        var previews = PREVIEWS.computeIfAbsent(server, unused -> new LinkedHashMap<>());
        previews.remove(player.getUuid());
        String dimension = input.get("dimension").getAsString();
        if (!dimension.equals(dimension(player))) return reply(false, "只能认购当前维度的选区。");
        var raw = input.getAsJsonArray("coordinates");
        if (raw.size() == 0 || raw.size() > 2048 || raw.size() % 2 != 0) return reply(false, "请选择 1 到 1024 个同维度区块。");
        int[] coordinates = new int[raw.size()];
        for (int i = 0; i < coordinates.length; i++) coordinates[i] = integer(raw.get(i).getAsString());
        var keys = LandApi.checkedInitialSelection(server, dimension, coordinates);
        String reason = check(server, saved, dimension, keys);
        if (!"ok".equals(reason)) return reply(false, reason);
        long now = System.nanoTime();
        previews.values().removeIf(p -> p.expired(now) || p.operator().get() == null);
        while (previews.size() >= MAX_PREVIEWS) previews.remove(previews.keySet().iterator().next());
        var p = new Preview(new java.lang.ref.WeakReference<>(player), dimension, keys, UUID.randomUUID(), now);
        previews.put(player.getUuid(), p);
        var result = reply(true, "确认后以服务器名义登记，不扣款，不开放公共权限。");
        result.addProperty("token", p.token().toString());
        result.addProperty("chunks", keys.size());
        result.addProperty("dimension", dimension);
        return result;
    }

    private static JsonObject confirm(MinecraftServer server, ServerPlayerEntity player, ClaimSavedData saved, UUID token) {
        var previews = PREVIEWS.get(server);
        var p = previews == null ? null : previews.get(player.getUuid());
        if (p == null || p.operator().get() != player || !p.token().equals(token)
                || p.expired(System.nanoTime()) || !p.dimension().equals(dimension(player)))
            return reply(false, "预览已失效，请重新预览当前维度选区。");
        if ("missing".equals(saved.initialGrantStatus("government:" + token))) {
            String reason = check(server, saved, p.dimension(), p.keys());
            if (!"ok".equals(reason)) { previews.remove(player.getUuid()); return reply(false, reason); }
        }
        String outcome = saved.allocateGovernment(player, p.keys(), token);
        if ("pending".equals(outcome)) {
            var retry = reply(false, "已登记，存盘尚待验证；请在预览有效期内点确认重试，不要重复占地。");
            retry.addProperty("retry", true);
            return retry;
        }
        previews.remove(player.getUuid());
        if (!"applied".equals(outcome)) return reply(false, "未完成服务器用地登记，请重新核对选区。");
        return reply(true, "已划入服务器用地；未扣款。");
    }

    private static String check(MinecraftServer server, ClaimSavedData saved, String dimension, List<ClaimKey> keys) {
        int[] coordinates = new int[keys.size() * 2];
        for (int i = 0; i < keys.size(); i++) {
            coordinates[i * 2] = keys.get(i).chunkX(); coordinates[i * 2 + 1] = keys.get(i).chunkZ();
        }
        // Revalidate world border and geometry instead of trusting an older preview.
        LandApi.checkedInitialSelection(server, dimension, coordinates);
        String local = saved.claims().checkInitialGrant(keys, GovernmentOwner.ID, 0);
        if (!"ok".equals(local)) return "选区已占用或受保护，不能代管或覆盖他人领地。";
        String market = MarketTradeBridge.governmentAllocationCheck(server, dimension, coordinates);
        return market == null || market.isBlank() ? "无法核对市场中的待结算地块，未划入服务器用地。" : market;
    }

    private static JsonObject list(ClaimSavedData saved, int requestedPage, String message) {
        var parcels = saved.claims().parcelsOwnedBy(GovernmentOwner.ID);
        int last = Math.max(0, parcels.size() - 1);
        int page = Math.max(0, Math.min(requestedPage, last));
        var result = reply(true, message);
        result.addProperty("page", page); result.addProperty("count", parcels.size());
        if (!parcels.isEmpty()) {
            var parcel = parcels.get(page); var key = parcel.chunks().get(0);
            var row = new JsonObject();
            row.addProperty("id", parcel.id().toString()); row.addProperty("name", parcel.name());
            row.addProperty("dimension", parcel.dimension()); row.addProperty("chunks", parcel.chunkCount());
            row.addProperty("x", key.chunkX()); row.addProperty("z", key.chunkZ());
            result.add("parcel", row);
        }
        return result;
    }

    private static String dimension(ServerPlayerEntity player) { return player.getWorld().getRegistryKey().getValue().toString(); }
    private static int integer(JsonObject input, String key) { return integer(input.get(key).getAsString()); }
    private static int integer(String raw) {
        if (!raw.matches("-?[0-9]{1,10}")) throw new IllegalArgumentException("integer");
        return Integer.parseInt(raw);
    }
    private static JsonObject reply(boolean ok, String message) {
        var result = new JsonObject(); result.addProperty("ok", ok); result.addProperty("reason", message); return result;
    }
}
