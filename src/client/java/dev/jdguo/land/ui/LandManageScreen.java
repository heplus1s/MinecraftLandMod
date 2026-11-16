package dev.jdguo.land.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** No balance changes here: edits only the explicit future initial-sale policy. */
public final class LandManageScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget price, currency;
    private boolean enabled;
    private boolean reading;
    private ButtonWidget enabledButton;
    private String localMessage = "";
    public LandManageScreen(Screen parent) { super(Text.literal("领地管理")); this.parent = parent; }
    @Override protected void init() {
        int w = Math.min(260, width - 24), x = (width - w) / 2;
        var config = LandQuotes.management();
        enabled = Boolean.parseBoolean(LandQuotes.text(config, "enabled", "false"));
        price = new TextFieldWidget(textRenderer, x, 50, w, 20, Text.literal("每区块默认价"));
        price.setMaxLength(48); price.setText(LandQuotes.text(config, "price", "")); addDrawableChild(price);
        currency = new TextFieldWidget(textRenderer, x, 90, w - 48, 20, Text.literal("计价货币"));
        currency.setMaxLength(128); currency.setText(LandQuotes.text(config, "currency", "")); addDrawableChild(currency);
        addDrawableChild(ButtonWidget.builder(Text.literal("选择"), button -> {
            var data = LandQuotes.management();
            if (!data.has("currencies") || !data.get("currencies").isJsonArray()) return;
            var choices = data.getAsJsonArray("currencies");
            if (choices.size() == 0) return;
            int next = 0;
            for (int i = 0; i < choices.size(); i++) {
                if (LandQuotes.text(choices.get(i).getAsJsonObject(), "id", "").equals(currency.getText())) { next = (i + 1) % choices.size(); break; }
            }
            currency.setText(LandQuotes.text(choices.get(next).getAsJsonObject(), "id", ""));
        }).dimensions(x + w - 44, 90, 44, 20).build());
        enabledButton = addDrawableChild(ButtonWidget.builder(Text.literal(enabled ? "初售：启用" : "初售：关闭"), button -> {
            enabled = !enabled; button.setMessage(Text.literal(enabled ? "初售：启用" : "初售：关闭"));
        }).dimensions(x, 118, w, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("保存默认地价"), button -> {
            JsonObject input = new JsonObject(); input.addProperty("action", "set"); input.addProperty("enabled", enabled);
            input.addProperty("price", price.getText().trim()); input.addProperty("currency", currency.getText().trim());
            localMessage = LandQuotes.send("manage", input) ? "已提交，等待服务器确认" : "服务器未提供管理接口";
        }).dimensions(x, 146, w, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("读取当前设置"), button -> request()).dimensions(x, 174, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close()).dimensions(x + w / 2 + 2, 174, w / 2 - 2, 20).build());
        request();
    }
    private void request() {
        reading = true;
        JsonObject input = new JsonObject(); input.addProperty("action", "get");
        localMessage = LandQuotes.send("manage", input) ? "正在读取服务器设置" : "服务器未提供管理接口";
    }
    public void receive(JsonObject data) {
        if (reading && data.has("price") && data.has("currency")) {
            price.setText(LandQuotes.text(data, "price", ""));
            currency.setText(LandQuotes.text(data, "currency", ""));
            enabled = Boolean.parseBoolean(LandQuotes.text(data, "enabled", "false"));
            enabledButton.setMessage(Text.literal(enabled ? "初售：启用" : "初售：关闭"));
        }
        reading = false;
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context); int x = (width - Math.min(260, width - 24)) / 2;
        context.drawTextWithShadow(textRenderer, "管理员：仅影响以后无主地初售", x, 18, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "默认单区块价格（正整数）", x, 38, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, "币种 ID 或中文货币名称", x, 78, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(LandQuotes.text(LandQuotes.management(), "reason", localMessage), width - 24), 12, 204, 0xFFFFAA);
        super.render(context, mouseX, mouseY, delta);
    }
}
