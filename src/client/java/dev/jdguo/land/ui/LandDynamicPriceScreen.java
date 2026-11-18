package dev.jdguo.land.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Separate administrator form. No price or ownership is calculated by the client. */
public final class LandDynamicPriceScreen extends Screen {
    private static final String[] KEYS = {"radius", "ownerBps", "listingBps", "saleBps", "decayHours", "listingMaxPercent"};
    private static final String[] LABELS = {"邻域半径（区块）", "独立业主上涨（基点）", "挂牌下行（基点）", "成交下行（基点）", "成交衰减时间（小时）", "计入挂牌最高参考比例%"};
    private static final String[] DEFAULTS = {"8", "500", "100", "300", "168", "120"};
    private static final String[] TIPS = {
            "范围 0～128 区块；1 区块为 16×16 格。仅统计同维度。",
            "范围 0～10000 基点。100 基点 = 1%。同一业主持有多块土地不重复计数。",
            "范围 0～10000 基点。有效挂牌造成较弱下行；撤销后不再产生该挂牌影响。",
            "范围 0～10000 基点。实际成交才形成成交下行信号；防止反复交易重复累积。",
            "范围 1～720 小时。成交影响随时间减弱，过期后不再计入。",
            "默认120%，范围1～10000%。高于不含挂牌压力参考价的比例上限，仅不计下行，不限制交易。跨币无有效现价不计。"
    };
    private final Screen parent;
    private final TextFieldWidget[] values = new TextFieldWidget[KEYS.length];
    private ButtonWidget enabledButton, readButton, backButton;
    private boolean enabled;
    private boolean waiting;
    private String message = "";

    public LandDynamicPriceScreen(Screen parent) { super(Text.literal("动态地价设置")); this.parent = parent; }

    @Override protected void init() {
        if (!LandQuotes.canManage() || width < 280 || height < 274) {
            addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close()).dimensions(8, 8, 80, 20).build());
            return;
        }
        int w = Math.min(300, width - 24), x = (width - w) / 2;
        enabledButton = addDrawableChild(ButtonWidget.builder(Text.literal("动态地价：关闭"), button -> {
            enabled = !enabled; updateToggle();
        }).dimensions(x, 46, w, 20).build());
        for (int i = 0; i < KEYS.length; i++) {
            values[i] = new TextFieldWidget(textRenderer, x + w - 70, 74 + i * 22, 70, 20, Text.literal(LABELS[i]));
            values[i].setMaxLength(5);
            values[i].setTextPredicate(value -> value.matches("[0-9]*"));
            values[i].setTooltip(Tooltip.of(Text.literal(TIPS[i])));
            values[i].setText(DEFAULTS[i]);
            addDrawableChild(values[i]);
        }
        int third = (w - 8) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> save()).dimensions(x, 214, third, 20).build());
        readButton = addDrawableChild(ButtonWidget.builder(Text.literal("读取"), button -> request()).dimensions(x + third + 4, 214, third, 20).build());
        backButton = addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close()).dimensions(x + (third + 4) * 2, 214, w - (third + 4) * 2, 20).build());
        request();
    }
    private void updateToggle() { enabledButton.setMessage(Text.literal(enabled ? "动态地价：开启" : "动态地价：关闭")); }
    private void request() {
        JsonObject input = new JsonObject(); input.addProperty("action", "get");
        waiting = true; setEditing(false);
        message = LandQuotes.send("manage", input) ? "正在读取服务器设置" : "服务器未提供管理接口";
    }
    private void save() {
        JsonObject input = new JsonObject(); input.addProperty("action", "setDynamic"); input.addProperty("dynamicEnabled", enabled);
        int[] max = {128, 10000, 10000, 10000, 720, 10000};
        for (int i = 0; i < KEYS.length; i++) {
            try {
                int value = Integer.parseInt(values[i].getText());
                if (value < (i >= 4 ? 1 : 0) || value > max[i]) throw new IllegalArgumentException();
                input.addProperty(KEYS[i], value);
            } catch (IllegalArgumentException invalid) { message = "请检查" + LABELS[i] + "；悬停输入框查看范围"; return; }
        }
        waiting = true; setEditing(false);
        message = LandQuotes.send("manage", input) ? "已提交，等待服务器确认" : "服务器未提供管理接口";
    }
    public void receive(JsonObject data) {
        if (!LandQuotes.canManage()) { LandQuotes.clearManagement(); close(); return; }
        if (enabledButton == null) return;
        boolean valid = Boolean.parseBoolean(LandQuotes.text(data, "ok", "false")) && data.has("dynamicEnabled");
        for (String key : KEYS) valid &= data.has(key);
        if (waiting && valid) {
            enabled = Boolean.parseBoolean(LandQuotes.text(data, "dynamicEnabled", "false")); updateToggle();
            for (int i = 0; i < KEYS.length; i++) values[i].setText(LandQuotes.text(data, KEYS[i], DEFAULTS[i]));
        }
        waiting = false; setEditing(valid);
        message = valid ? LandQuotes.text(data, "reason", "已读取当前设置") : LandQuotes.text(data, "reason", "服务器尚未提供动态地价设置，请更新市场模组");
    }
    private void setEditing(boolean active) {
        for (var child : children()) if (child instanceof ClickableWidget widget && child != readButton && child != backButton) widget.active = active;
        for (TextFieldWidget value : values) if (value != null) value.setEditable(active);
    }
    @Override public void tick() { if (!LandQuotes.canManage()) { LandQuotes.clearManagement(); close(); } }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        if (!LandQuotes.canManage() || width < 280 || height < 274) {
            String hint = LandQuotes.canManage() ? "请调小界面尺寸（至少 280×274）" : "无管理员权限";
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(hint, Math.max(1, width - 16)), 8, 40, 0xFFFFAA);
            super.render(context, mouseX, mouseY, delta); return;
        }
        int w = Math.min(300, width - 24), x = (width - w) / 2;
        context.drawTextWithShadow(textRenderer, "管理员 · 动态地价", x, 15, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "100 基点 = 1%；不按建设量计价", x, 31, 0xCCCCCC);
        for (int i = 0; i < LABELS.length; i++) context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(LABELS[i], w - 78), x, 80 + i * 22, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, "仅影响系统报价，二手挂牌价由卖家确认", x, 240, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(message, width - 24), 12, 256, 0xFFFFAA);
        super.render(context, mouseX, mouseY, delta);
    }
}
