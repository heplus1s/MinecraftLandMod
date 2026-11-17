package dev.jdguo.land.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Display names never replace stable ledger identifiers. */
public final class CurrencyLabels {
    private CurrencyLabels() {}
    public static String label(String id, JsonObject data) {
        if (data.has("currencies") && data.get("currencies").isJsonArray()) {
            for (var value : data.getAsJsonArray("currencies")) {
                JsonObject row = value.getAsJsonObject();
                if (id.equals(LandQuotes.text(row, "id", ""))) {
                    String name = LandQuotes.text(row, "label", "");
                    if (id.equals("note") && (name.isBlank() || name.equals("note") || name.equals("银行券"))) name = "央行券";
                    if (name.isBlank() || name.equals(id)) name = fallback(id);
                    return name + (id.startsWith("personal:") ? "（个人币）" : id.equals("note") ? "（央行）" : "（实物货币）");
                }
            }
        }
        return fallback(id);
    }
    private static String fallback(String id) {
        return switch (id) { case "note" -> "央行券"; case "diamond" -> "钻石"; case "copper" -> "铜锭"; default -> id.startsWith("personal:") ? "个人币（名称暂不可用）" : "未选择货币"; };
    }
}
