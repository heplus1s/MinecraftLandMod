package dev.jdguo.land.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jdguo.land.trade.LandQuoteChannel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;

/** Quotes are server facts tied to a selection generation, never client calculations. */
public final class LandQuotes {
    private static int selectedGeneration = -1;
    private static int age;
    private static JsonObject quote = new JsonObject();
    private static JsonObject management = new JsonObject();
    private LandQuotes() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LandQuoteChannel.RESPONSE, (client, handler, buf, sender) -> {
            final int generation;
            final String operation, json;
            try { generation = buf.readVarInt(); operation = buf.readString(32); json = buf.readString(32767); } catch (RuntimeException malformed) { return; }
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.player == null) return;
                try {
                    JsonObject value = JsonParser.parseString(json).getAsJsonObject();
                    if (operation.equals("manage")) { management = value; if (client.currentScreen instanceof LandManageScreen screen) screen.receive(value); }
                    else if (generation == LandPick.generation()) { quote = value; age = 0; }
                } catch (RuntimeException ignored) { }
            });
        });
    }
    public static void reset() { selectedGeneration = -1; age = 0; quote = new JsonObject(); management = new JsonObject(); }
    public static void tick(MinecraftClient client) {
        if (client.player == null || LandPick.selected().isEmpty()) return;
        if (selectedGeneration == LandPick.generation() && ++age < 100) return;
        selectedGeneration = LandPick.generation();
        age = 0;
        quote = new JsonObject();
        var keys = LandPick.selected();
        if (keys.size() > 1024) { quote.addProperty("reason", "报价范围最多 1024 块，请缩小选区。"); return; }
        String dimension = keys.get(0).dimension();
        JsonArray coords = new JsonArray();
        for (var key : keys) {
            if (!dimension.equals(key.dimension())) { quote.addProperty("reason", "不能跨维度报价。"); return; }
            coords.add(key.chunkX()); coords.add(key.chunkZ());
        }
        JsonObject input = new JsonObject(); input.addProperty("dimension", dimension); input.add("coordinates", coords);
        if (!send("quote", input)) quote.addProperty("reason", "服务器未提供选区报价接口。");
    }
    public static boolean send(String operation, JsonObject data) {
        if (!ClientPlayNetworking.canSend(LandQuoteChannel.REQUEST)) return false;
        var packet = PacketByteBufs.create(); packet.writeVarInt(LandPick.generation());
        packet.writeString(operation); packet.writeString(data.toString(), 32767);
        ClientPlayNetworking.send(LandQuoteChannel.REQUEST, packet); return true;
    }
    public static String summary() {
        if (LandPick.selected().isEmpty()) return "请选择地块查看报价";
        if (selectedGeneration != LandPick.generation()) return "正在查询选区报价…";
        String total = text(quote, "total", "");
        if (!total.isBlank()) return "成交总价 " + total + " " + text(quote, "currencyName", text(quote, "currency", ""))
                + " · " + text(quote, "chunks", "?") + " 块（完整成交范围）"
                + (Boolean.parseBoolean(text(quote, "buyable", "false")) ? "" : " · " + text(quote, "reason", "不可购买"));
        return text(quote, "reason", "正在查询选区报价…");
    }
    public static JsonObject management() { return management; }
    public static String text(JsonObject object, String key, String fallback) {
        try { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; }
        catch (RuntimeException invalid) { return fallback; }
    }
}
