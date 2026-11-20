package dev.jdguo.land.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jdguo.land.api.LandApi;
import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.claim.ClaimMap;
import dev.jdguo.land.claim.ParcelInfo;
import dev.jdguo.land.claim.GovernmentOwner;
import dev.jdguo.land.integration.MarketTradeBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public final class GovernmentLand {
    private static final long CONFIRM_NANOS = 90_000_000_000L;
    private static final int MAX_PREVIEWS = 64;
    private static final Map<MinecraftServer, LinkedHashMap<UUID, Preview>> PREVIEWS = new WeakHashMap<>();
    private record Preview(java.lang.ref.WeakReference<ServerPlayerEntity> operator, String dimension, List<ClaimKey> keys, UUID token, long created, Disposal disposal) {
        boolean expired(long now) { return now - created >= CONFIRM_NANOS; }
    }
    record Disposal(String kind, UUID target, UUID source, List<ClaimKey> keys, String name,
                    List<ClaimKey> targetBefore, List<ClaimKey> sourceBefore) {
        ClaimMap.Result checkOrApply(ClaimMap claims, boolean apply) {
            return switch (kind) {
                case "previewSplit" -> claims.split(GovernmentOwner.ID, keys, name, apply);
                case "previewMerge" -> claims.merge(GovernmentOwner.ID, target, source, apply);
                case "previewRelease", "previewReleaseParcel" -> claims.releaseGovernment(keys, apply);
                default -> throw new IllegalArgumentException("unknown disposal");
            };
        }
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
            case "previewSplit", "previewMerge", "previewRelease", "previewReleaseParcel" ->
                    previewDisposal(server, player, saved, action, input).toString();
            case "openListing" -> {
                UUID parcel = UUID.fromString(input.get("parcel").getAsString());
                if (LandApi.governmentSaleScope(server, player.getUuid(), parcel) == null)
                    yield reply(false, "只能挂牌服务器宗地。").toString();
                boolean opened = MarketTradeBridge.openGovernmentListing(player, parcel);
                yield reply(opened, opened ? "正在打开市场挂牌。" : "请更新市场模组后再挂牌。").toString();
            }
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
        var p = new Preview(new java.lang.ref.WeakReference<>(player), dimension, keys, UUID.randomUUID(), now, null);
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
            String reason = p.disposal() == null ? check(server, saved, p.dimension(), p.keys())
                    : validateDisposal(server, saved, p.dimension(), p.disposal());
            if (!"ok".equals(reason)) { previews.remove(player.getUuid()); return reply(false, reason); }
        }
        String outcome = p.disposal() == null ? saved.allocateGovernment(player, p.keys(), token)
                : saved.disposeGovernment(player, p.disposal(), token);
        if ("pending".equals(outcome)) {
            var retry = reply(false, "操作已登记，存盘尚待验证；请在预览有效期内点确认重试。");
            retry.addProperty("retry", true);
            return retry;
        }
        previews.remove(player.getUuid());
        if (!"applied".equals(outcome)) return reply(false, "未完成操作，请重新核对选区、挂牌状态和权限。");
        return reply(true, p.disposal() == null ? "已划入服务器用地；未扣款。" : "服务器用地操作完成；未扣款。");
    }

    private static JsonObject previewDisposal(MinecraftServer server, ServerPlayerEntity player, ClaimSavedData saved,
                                              String action, JsonObject input) {
        var previews = PREVIEWS.computeIfAbsent(server, unused -> new LinkedHashMap<>());
        previews.remove(player.getUuid());
        String world = dimension(player);
        ParcelInfo target, source = null;
        List<ClaimKey> keys;
        String name = input.has("name") ? input.get("name").getAsString() : "";
        if ("previewMerge".equals(action)) {
            target = saved.claims().info(UUID.fromString(input.get("target").getAsString()));
            source = saved.claims().info(UUID.fromString(input.get("source").getAsString()));
            if (!ownedIn(target, world) || !ownedIn(source, world)) return reply(false, "只能合并当前维度的两块服务器宗地。");
            keys = java.util.stream.Stream.concat(target.chunks().stream(), source.chunks().stream()).distinct().toList();
        } else if ("previewReleaseParcel".equals(action)) {
            target = saved.claims().info(UUID.fromString(input.get("parcel").getAsString()));
            if (!ownedIn(target, world)) return reply(false, "只能释放当前维度的服务器宗地。");
            keys = target.chunks();
        } else {
            if (!world.equals(input.get("dimension").getAsString())) return reply(false, "只能操作当前维度的选区。");
            var raw = input.getAsJsonArray("coordinates");
            if (raw.size() == 0 || raw.size() > 2048 || raw.size() % 2 != 0) return reply(false, "请选择 1 到 1024 个同维度区块。");
            int[] coords = new int[raw.size()];
            for (int i = 0; i < coords.length; i++) coords[i] = integer(raw.get(i).getAsString());
            keys = LandApi.checkedInitialSelection(server, world, coords);
            var first = keys.get(0);
            target = LandApi.parcelAt(server, world, first.chunkX(), first.chunkZ());
            if (!ownedIn(target, world) || !new HashSet<>(target.chunks()).containsAll(keys))
                return reply(false, "只能选中同一块服务器宗地的区块。");
        }
        var t = new Disposal(action, target.id(), source == null ? null : source.id(), keys, name,
                target.chunks(), source == null ? List.of() : source.chunks());
        String checked = validateDisposal(server, saved, world, t);
        if (!"ok".equals(checked)) return reply(false, checked);
        long now = System.nanoTime();
        previews.values().removeIf(p -> p.expired(now) || p.operator().get() == null);
        while (previews.size() >= MAX_PREVIEWS) previews.remove(previews.keySet().iterator().next());
        var token = UUID.randomUUID();
        previews.put(player.getUuid(), new Preview(new java.lang.ref.WeakReference<>(player), world, keys, token, now, t));
        String message = switch (action) {
            case "previewSplit" -> "确认后从 " + nameOf(target) + " 分割为独立服务器宗地，现有成员权限不变。";
            case "previewMerge" -> "将 " + nameOf(source) + " 并入 " + nameOf(target) + "，保留目标名称和各区块成员。";
            default -> "确认后选区变为无主地，原领地保护将消失，不退款。宗地：" + nameOf(target);
        };
        var result = reply(true, message);
        result.addProperty("token", token.toString()); result.addProperty("chunks", keys.size()); result.addProperty("dimension", world);
        result.addProperty("target", target.id().toString()); result.addProperty("targetName", nameOf(target));
        if (source != null) {
            result.addProperty("source", source.id().toString()); result.addProperty("sourceName", nameOf(source));
        }
        return result;
    }

    private static String nameOf(ParcelInfo parcel) {
        return (parcel.name().isBlank() ? "宗地" : parcel.name()) + " #" + parcel.shortId();
    }

    private static boolean ownedIn(ParcelInfo parcel, String dimension) {
        return parcel != null && GovernmentOwner.is(parcel.owner()) && parcel.dimension().equals(dimension);
    }

    private static String validateDisposal(MinecraftServer server, ClaimSavedData saved, String world, Disposal planned) {
        var current = saved.claims().info(planned.target());
        if (!ownedIn(current, world) || !current.chunks().equals(planned.targetBefore()))
            return "目标宗地或范围已变化，请重新预览。";
        if (planned.source() != null) {
            var source = saved.claims().info(planned.source());
            if (!ownedIn(source, world) || !source.chunks().equals(planned.sourceBefore()))
                return "源宗地或范围已变化，请重新预览。";
        }
        if (planned.keys().stream().anyMatch(key -> {
            var claim = saved.claims().get(key.dimension(), key.chunkX(), key.chunkZ());
            return claim == null || !GovernmentOwner.is(claim.owner());
        })) return "选区包含非服务器领地，未执行处置。";
        // Old Market versions cannot safely verify pending changes; the live gates below
        // independently reject existing OPEN/RESERVED listings for the affected parcels.
        for (int lo = 0; lo < planned.keys().size(); lo += 1024) {
            var batch = planned.keys().subList(lo, Math.min(lo + 1024, planned.keys().size()));
            int[] coords = coordinates(batch);
            LandApi.checkedInitialSelection(server, world, coords);
            String gate = MarketTradeBridge.governmentAllocationCheck(server, world, coords);
            if (!"ok".equals(gate)) return gate == null || gate.isBlank() ? "无法核对市场交易。" : gate;
        }
        var result = planned.checkOrApply(saved.claims(), false);
        return result.succeeded() ? "ok" : switch (result.denial()) {
            case GATE -> "宗地受保护或正在挂牌、结算，暂不能处置。";
            case ENTIRE_PARCEL -> "分割只能选择同一宗地的一部分区块。";
            case NOT_ADJACENT -> "两块宗地必须相邻才能合并。";
            case SAME_PARCEL -> "请分别选择两个不同的宗地。";
            default -> "宗地、选区或名称无效，未修改领地。";
        };
    }

    private static int[] coordinates(List<ClaimKey> keys) {
        int[] coordinates = new int[keys.size() * 2];
        for (int i = 0; i < keys.size(); i++) {
            coordinates[i * 2] = keys.get(i).chunkX(); coordinates[i * 2 + 1] = keys.get(i).chunkZ();
        }
        return coordinates;
    }

    private static String check(MinecraftServer server, ClaimSavedData saved, String dimension, List<ClaimKey> keys) {
        int[] coordinates = coordinates(keys);
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
