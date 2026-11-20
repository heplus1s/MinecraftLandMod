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
    private String currencyId = "";
    private boolean enabled;
    private boolean reading;
    private ButtonWidget enabledButton, readButton, backButton;
    private String localMessage = "";
    public LandManageScreen(Screen parent) { super(Text.literal("领地管理")); this.parent = parent; }
    @Override protected void init() {
        if (!LandQuotes.canManage()) {
            LandQuotes.clearManagement();
            addDrawableChild(ButtonWidget.builder(Text.literal("无管理员权限，返回"), button -> close()).dimensions(8, 8, 160, 20).build());
            return;
        }
        if (width < 280 || height < 250) {
            addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close()).dimensions(8, 8, 80, 20).build());
            return;
        }
        int w = Math.min(260, width - 24), x = (width - w) / 2;
        var config = LandQuotes.management();
        enabled = Boolean.parseBoolean(LandQuotes.text(config, "enabled", "false"));
        price = new TextFieldWidget(textRenderer, x, 50, w, 20, Text.literal("每区块默认价"));
        price.setMaxLength(48); price.setText(LandQuotes.text(config, "price", "")); addDrawableChild(price);
        currency = new TextFieldWidget(textRenderer, x, 90, w - 48, 20, Text.literal("计价货币"));
        currency.setMaxLength(128); currency.setEditable(false);
        currencyId = LandQuotes.text(config, "currency", "");
        currency.setText(CurrencyLabels.label(currencyId, config)); addDrawableChild(currency);
        addDrawableChild(ButtonWidget.builder(Text.literal("选择"), button -> {
            var data = LandQuotes.management();
            if (!data.has("currencies") || !data.get("currencies").isJsonArray()) return;
            var choices = data.getAsJsonArray("currencies");
            if (choices.size() == 0) return;
            int next = 0;
            for (int i = 0; i < choices.size(); i++) {
                if (LandQuotes.text(choices.get(i).getAsJsonObject(), "id", "").equals(currencyId)) { next = (i + 1) % choices.size(); break; }
            }
            currencyId = LandQuotes.text(choices.get(next).getAsJsonObject(), "id", "");
            currency.setText(CurrencyLabels.label(currencyId, data));
        }).dimensions(x + w - 44, 90, 44, 20).build());
        enabledButton = addDrawableChild(ButtonWidget.builder(Text.literal(enabled ? "无主地块出售：开启" : "无主地块出售：关闭"), button -> {
            enabled = !enabled; button.setMessage(Text.literal(enabled ? "无主地块出售：开启" : "无主地块出售：关闭"));
        }).dimensions(x, 118, w, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("保存默认地价"), button -> {
            JsonObject input = new JsonObject(); input.addProperty("action", "set"); input.addProperty("enabled", enabled);
            input.addProperty("price", price.getText().trim()); input.addProperty("currency", currencyId);
            localMessage = LandQuotes.send("manage", input) ? "已提交，等待服务器确认" : "服务器未提供管理接口";
        }).dimensions(x, 146, w, 20).build());
        readButton = addDrawableChild(ButtonWidget.builder(Text.literal("读取当前设置"), button -> request()).dimensions(x, 174, w / 2 - 2, 20).build());
        backButton = addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close()).dimensions(x + w / 2 + 2, 174, w / 2 - 2, 20).build());
        int third = (w - 8) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("填入建议地价"), button -> {
            var data = LandQuotes.management();
            price.setText(LandQuotes.text(data, "recommendedPrice", "32"));
            currencyId = LandQuotes.text(data, "recommendedCurrency", "note");
            currency.setText(CurrencyLabels.label(currencyId, data));
            localMessage = "仅填入建议，点击保存后才生效；不会修改二手挂牌";
        }).dimensions(x, 202, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("动态地价设置"), button ->
                client.setScreen(new LandDynamicPriceScreen(this))).dimensions(x + third + 4, 202, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("维度地价设置"), button ->
                client.setScreen(new LandDimensionPriceScreen(this))).dimensions(x + 2 * (third + 4), 202, w - 2 * (third + 4), 20).build());
        request();
    }
    private void request() {
        reading = true;
        setEditing(false);
        JsonObject input = new JsonObject(); input.addProperty("action", "get");
        localMessage = LandQuotes.send("manage", input) ? "正在读取服务器设置" : "服务器未提供管理接口";
    }
    public void receive(JsonObject data) {
        if (!LandQuotes.canManage()) { LandQuotes.clearManagement(); close(); return; }
        if (price == null || currency == null || enabledButton == null) return;
        boolean valid = data.has("ok") && data.get("ok").getAsBoolean() && data.has("price") && data.has("currency");
        if (reading && valid) {
            price.setText(LandQuotes.text(data, "price", ""));
            currencyId = LandQuotes.text(data, "currency", "");
            currency.setText(CurrencyLabels.label(currencyId, data));
            enabled = Boolean.parseBoolean(LandQuotes.text(data, "enabled", "false"));
            enabledButton.setMessage(Text.literal(enabled ? "无主地块出售：开启" : "无主地块出售：关闭"));
        }
        reading = false;
        setEditing(valid);
        localMessage = LandQuotes.text(data, "reason", "设置响应已收到");
    }
    private void setEditing(boolean active) {
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.widget.ClickableWidget widget && child != readButton && child != backButton) widget.active = active;
        }
        if (price != null) price.setEditable(active);
    }
    @Override public void tick() {
        if (!LandQuotes.canManage()) { LandQuotes.clearManagement(); close(); }
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        if (!LandQuotes.canManage()) { super.render(context, mouseX, mouseY, delta); return; }
        if (width < 280 || height < 250) {
            context.drawTextWithShadow(textRenderer, "请调小界面尺寸后设置（至少 280 × 250）", 8, 40, 0xFFFFAA);
            super.render(context, mouseX, mouseY, delta); return;
        }
        int x = (width - Math.min(260, width - 24)) / 2;
        context.drawTextWithShadow(textRenderer, "管理员：仅设置未来无主地块出售", x, 18, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "单区块价格（16×16 格，正整数）", x, 38, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, "计价货币（点击选择切换）", x, 78, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(localMessage.isBlank() ? LandQuotes.text(LandQuotes.management(), "reason", "") : localMessage, width - 24), 12, 232, 0xFFFFAA);
        super.render(context, mouseX, mouseY, delta);
    }
}
