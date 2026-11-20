package dev.jdguo.land.trade;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jdguo.land.world.GovernmentLand;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Read-only quote and permission-checked management transport, separate from legacy packets. */
public final class LandQuoteChannel {
    public static final Identifier REQUEST = new Identifier("landmod", "quote_request");
    public static final Identifier RESPONSE = new Identifier("landmod", "quote_response");
    public static final String GOVERNMENT_MANAGE = "government_land";
    public static final String DIMENSION_MANAGE = "dimension_manage";
    private LandQuoteChannel() {}
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, response) -> {
            final int generation;
            final String operation, payload;
            try {
                generation = buf.readVarInt();
                operation = buf.readString(32);
                payload = buf.readString(32767);
            } catch (RuntimeException malformed) { return; }
            server.execute(() -> {
                if (!dev.jdguo.land.network.RequestAccess.current(server.getPlayerManager().getPlayer(player.getUuid()), player, player.networkHandler, handler)) return;
                if (!ServerPlayNetworking.canSend(player, RESPONSE)) return;
                String result;
                try {
                    if (operation.equals(dev.jdguo.land.network.CurrentWorldContext.QUOTE_OPERATION)) {
                        result = dev.jdguo.land.network.CurrentWorldContext.quoteContext(player, payload);
                    } else if (GOVERNMENT_MANAGE.equals(operation)) {
                        result = GovernmentLand.request(server, player, payload);
                    } else {
                    Class<?> api = Class.forName("dev.jdguo.market.api.LandMarketTerminal");
                    if (operation.equals("quote")) {
                        JsonObject input = JsonParser.parseString(payload).getAsJsonObject();
                        var array = input.getAsJsonArray("coordinates");
                        if (array.size() > 2048 || array.size() % 2 != 0) throw new IllegalArgumentException("selection");
                        int[] coordinates = new int[array.size()];
                        for (int i = 0; i < coordinates.length; i++) coordinates[i] = array.get(i).getAsInt();
                        result = (String) api.getMethod("quote", MinecraftServer.class, UUID.class, String.class, int[].class)
                                .invoke(null, server, player.getUuid(), input.get("dimension").getAsString(), coordinates);
                    } else if (operation.equals("manage") || operation.equals(DIMENSION_MANAGE)) {
                        if (!dev.jdguo.land.network.RequestAccess.mayManage(true, player.hasPermissionLevel(2))) {
                            result = error("只有管理员可以修改默认地价。");
                        } else {
                            result = (String) api.getMethod("manage", MinecraftServer.class, UUID.class, String.class)
                                    .invoke(null, server, player.getUuid(), payload);
                        }
                    } else return;
                    }
                } catch (ReflectiveOperationException | LinkageError missing) {
                    result = error("市场版本未提供报价或管理接口，请更新市场模组。");
                } catch (RuntimeException invalid) {
                    result = error("请求无效，请重新选择地块。");
                }
                if (operation.equals("quote")) {
                    try {
                        var diagnostic = JsonParser.parseString(result).getAsJsonObject();
                        diagnostic.addProperty("serverDimension", player.getWorld().getRegistryKey().getValue().toString());
                        diagnostic.addProperty("requestedDimension", JsonParser.parseString(payload).getAsJsonObject().get("dimension").getAsString());
                        result = diagnostic.toString();
                    } catch (RuntimeException invalid) { /* Preserve the original error reply. */ }
                }
                var out = PacketByteBufs.create();
                out.writeVarInt(generation);
                out.writeString(operation);
                out.writeString(result == null ? error("暂无有效报价。") : result, 32767);
                ServerPlayNetworking.send(player, RESPONSE, out);
            });
        });
    }
    private static String error(String message) {
        JsonObject value = new JsonObject();
        value.addProperty("buyable", false);
        value.addProperty("reason", message);
        return value.toString();
    }
}
