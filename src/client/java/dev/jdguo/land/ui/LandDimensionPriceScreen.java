package dev.jdguo.land.ui;

import com.google.gson.JsonObject;
import dev.jdguo.land.trade.LandQuoteChannel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Server-authoritative initial-sale base settings; not tied to the dynamic pricing switch. */
public final class LandDimensionPriceScreen extends Screen {
    private static final String[] KEYS = {"netherPercent", "endCenterPercent", "endCenterRadius"};
    private static final String[] LABELS = {"下界基价（%）", "末地主岛基价（%）", "主岛计价半径（格）"};
    private static final String[] TIPS = {
        "100 为主世界基价，125 为 1.25 倍。支持 100～1000。",
        "仅作用于末地主岛计价圈内。200 为 2 倍；支持 100～1000。",
        "以末地 (0, 0) 为圆心，按区块中心判断。支持 0～30000000；0 表示不加价。"
    };
    private final Screen parent;
    private final TextFieldWidget[] values = new TextFieldWidget[KEYS.length];
    private ButtonWidget save, reload;
    private int waitingTicks;
    private int requestSequence;
    private String revision = "";
    private String status = "";
    private boolean ready;

    public LandDimensionPriceScreen(Screen parent) { super(Text.literal("维度地价设置")); this.parent = parent; }

    @Override protected void init() {
        save = reload = null;
        ready = false;
        waitingTicks = 0;
        revision = "";
        if (!LandQuotes.canManage() || width < 270 || height < 186) {
            addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close()).dimensions(8, 8, 70, 20).build());
            return;
        }
        int w = formWidth(), x = (width - w) / 2;
        for (int i = 0; i < KEYS.length; i++) {
            var field = new TextFieldWidget(textRenderer, x + w - 90, 49 + 25 * i, 90, 19, Text.literal(LABELS[i]));
            field.setMaxLength(i == 2 ? 8 : 4);
            field.setTextPredicate(text -> text.matches("[0-9]*"));
            field.setTooltip(Tooltip.of(Text.literal(TIPS[i])));
            values[i] = addDrawableChild(field);
        }
        int cell = (w - 8) / 3;
        save = addDrawableChild(ButtonWidget.builder(Text.literal("保存"), b -> save()).dimensions(x, 130, cell, 20).build());
        reload = addDrawableChild(ButtonWidget.builder(Text.literal("重新读取"), b -> request(new JsonObject(), false)).dimensions(x + cell + 4, 130, cell, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close()).dimensions(x + 2 * (cell + 4), 130, w - 2 * (cell + 4), 20).build());
        request(new JsonObject(), false);
    }

    private int formWidth() { return Math.min(310, width - 24); }

    private void save() {
        if (!ready || waitingTicks != 0) return;
        var data = new JsonObject();
        for (int i = 0; i < KEYS.length; i++) {
            try {
                int n = Integer.parseInt(values[i].getText());
                if (n < (i == 2 ? 0 : 100) || n > (i == 2 ? 30_000_000 : 1000)) throw new NumberFormatException();
                data.addProperty(KEYS[i], n);
            } catch (NumberFormatException invalid) {
                status = LABELS[i] + "超出范围，请查看输入框提示";
                return;
            }
        }
        if (!revision.isEmpty()) data.addProperty("revision", revision);
        request(data, true);
    }

    private void request(JsonObject data, boolean saving) {
        ready = false;
        data.addProperty("action", saving ? "setDimension" : "get");
        requestSequence = LandQuotes.nextDimensionManagementSequence();
        boolean sent = LandQuotes.send(LandQuoteChannel.DIMENSION_MANAGE, data, requestSequence);
        waitingTicks = sent ? 100 : 0;
        status = sent ? (saving ? "正在保存…" : "正在读取…") : "服务器未提供维度地价接口";
        setEditing();
    }

    public void receive(int sequence, JsonObject data) {
        if (!LandQuotes.canManage() || save == null || waitingTicks == 0 || sequence != requestSequence) return;
        waitingTicks = 0;
        boolean ok = Boolean.parseBoolean(LandQuotes.text(data, "ok", "false"));
        ready = ok;
        for (String key : KEYS) ready &= !LandQuotes.text(data, key, "").isBlank();
        if (ready) {
            for (int i = 0; i < KEYS.length; i++) values[i].setText(LandQuotes.text(data, KEYS[i], ""));
            revision = LandQuotes.text(data, "revision", "");
        }
        status = ready ? LandQuotes.text(data, "reason", "已读取服务器设置")
                : ok ? "服务器市场模组不支持维度地价"
                     : LandQuotes.text(data, "reason", "读取失败，请重试");
        setEditing();
    }

    private void setEditing() {
        save.active = ready && waitingTicks == 0;
        reload.active = waitingTicks == 0;
        for (var field : values) field.setEditable(save.active);
    }

    @Override public void tick() {
        if (!LandQuotes.canManage()) { LandQuotes.clearManagement(); close(); return; }
        if (waitingTicks > 0 && --waitingTicks == 0) {
            status = "服务器未确认，请重新读取（旧版服务端需更新）";
            setEditing();
        }
    }

    @Override public void close() { client.setScreen(parent); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int w = formWidth(), x = (width - w) / 2;
        if (save == null) {
            context.drawTextWithShadow(textRenderer, LandQuotes.canManage() ? "界面至少需要 270 × 186" : "无管理员权限", 8, 36, 0xFFFFAA);
        } else {
            context.drawTextWithShadow(textRenderer, "管理员 · 维度地价设置", x, 14, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "仅系统初售 · 主世界及末地外岛为 100%", x, 30, 0xCCCCCC);
            for (int i = 0; i < KEYS.length; i++)
                context.drawTextWithShadow(textRenderer, LABELS[i], x, 54 + 25 * i, 0xDDDDDD);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, w), x, 158, 0xFFFFAA);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
