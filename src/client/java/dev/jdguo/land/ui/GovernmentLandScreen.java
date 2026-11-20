package dev.jdguo.land.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.trade.LandQuoteChannel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

final class GovernmentLandScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget name;
    private ButtonWidget preview, confirm, previous, next, rename, permissions, locate;
    private String note = "", token = "", action = "";
    private int requestId, waitTicks, page, count;
    private boolean waiting;
    private JsonObject parcel;
    private List<ClaimKey> scope = List.of();

    GovernmentLandScreen(Screen parent) { super(Text.literal("服务器用地")); this.parent = parent; }

    @Override protected void init() {
        if (width < 280 || height < 236 || !LandQuotes.canManage()) {
            button("返回", 8, 8, 80, this::close);
            return;
        }
        int w = Math.min(330, width - 24), x = (width - w) / 2;
        int half = (w - 4) / 2, third = (w - 8) / 3;
        preview = button("预览系统认购", x, 41, half, this::preview);
        confirm = button("确认划入服务器用地", x + half + 4, 41, w - half - 4, () -> {
            var data = action("confirm"); data.addProperty("token", token); send(data);
        });
        previous = button("上一宗", x, 128, third, () -> read(page - 1));
        next = button("下一宗", x + third + 4, 128, third, () -> read(page + 1));
        locate = button("地图定位", x + 2 * (third + 4), 128, w - 2 * (third + 4), this::locate);
        String draft = name == null ? "" : name.getText();
        name = new TextFieldWidget(textRenderer, x, 152, w - 58, 20, Text.literal("宗地新名称"));
        name.setMaxLength(64); name.setText(draft); addDrawableChild(name);
        rename = button("改名", x + w - 54, 152, 54, this::rename);
        permissions = button("管理选中区块权限", x, 176, half, this::permissions);
        button("刷新服务器清单", x + half + 4, 176, w - half - 4, () -> read(page));
        button("返回", x, height - 25, w, this::close);
        if (parcel == null && !waiting && token.isBlank()) read(page);
        refreshButtons();
    }

    private ButtonWidget button(String label, int x, int y, int w, Runnable run) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> run.run()).dimensions(x, y, w, 20).build());
    }
    private static JsonObject action(String value) {
        var result = new JsonObject(); result.addProperty("action", value); return result;
    }
    private void read(int nextPage) {
        var data = action("list"); data.addProperty("page", nextPage); send(data);
    }
    private void preview() {
        token = "";
        scope = LandPick.selected();
        if (scope.isEmpty() || scope.size() > 1024) { note = "先在地图选择 1 到 1024 个同维度区块。"; return; }
        String dimension = scope.get(0).dimension();
        if (scope.stream().anyMatch(key -> !dimension.equals(key.dimension()))) { note = "选区不能跨维度。"; return; }
        var coordinates = new JsonArray();
        for (var key : scope) { coordinates.add(key.chunkX()); coordinates.add(key.chunkZ()); }
        var data = action("preview"); data.addProperty("dimension", dimension); data.add("coordinates", coordinates); send(data);
    }
    private void rename() {
        if (parcel == null || name.getText().isBlank()) return;
        var data = action("rename"); data.addProperty("page", page);
        data.addProperty("parcel", parcel.get("id").getAsString()); data.addProperty("name", name.getText().trim()); send(data);
    }
    private void locate() {
        if (parcel == null) return;
        String dimension = parcel.get("dimension").getAsString();
        if (!dimension.equals(ClientWorldContext.currentDimension(client))) { note = "请先进入该维度；此操作不会传送。"; return; }
        client.setScreen(new LandBoardScreen(this, new ClaimKey(dimension, parcel.get("x").getAsInt(), parcel.get("z").getAsInt())));
    }
    private void permissions() {
        if (LandPick.selected().size() != 1) { note = "请在地图中选中一个服务器区块后重开此界面。"; return; }
        var key = LandPick.selected().get(0);
        // Force the government-only server branch; this screen cannot administer private land.
        client.setScreen(new ClaimPermissionsScreen(this, key, true));
    }

    private void send(JsonObject data) {
        if (waiting) return;
        action = data.get("action").getAsString();
        requestId = LandQuotes.nextDimensionManagementSequence();
        waiting = LandQuotes.send(LandQuoteChannel.GOVERNMENT_MANAGE, data, requestId);
        waitTicks = 0;
        note = waiting ? "等待服务器确认…" : "服务器未支持服务器用地管理。";
        refreshButtons();
    }

    void receive(int generation, JsonObject data) {
        if (!waiting || generation != requestId || !LandQuotes.canManage()) return;
        waiting = false;
        note = LandQuotes.text(data, "reason", "无响应");
        boolean ok = data.has("ok") && data.get("ok").getAsBoolean();
        if (ok && "preview".equals(action)) token = LandQuotes.text(data, "token", "");
        if ("confirm".equals(action)) {
            if (!data.has("retry") || !data.get("retry").getAsBoolean()) token = "";
            if (ok) {
                String done = note;
                read(page);
                note = done;
            }
        }
        if (ok && data.has("page")) {
            page = data.get("page").getAsInt(); count = data.get("count").getAsInt();
            parcel = data.has("parcel") ? data.getAsJsonObject("parcel") : null;
            if (name != null) name.setText("");
        }
        refreshButtons();
    }

    private void refreshButtons() {
        if (preview == null) return;
        preview.active = !waiting;
        confirm.active = !waiting && !token.isBlank() && scope.equals(LandPick.selected());
        previous.active = !waiting && page > 0;
        next.active = !waiting && page + 1 < count;
        locate.active = rename.active = !waiting && parcel != null;
        permissions.active = !waiting;
    }

    @Override public void tick() {
        if (!LandQuotes.canManage()) { close(); return; }
        if (name != null) name.tick();
        if (waiting && ++waitTicks >= 200) { waiting = false; note = "未收到确认，请刷新核对。"; }
        refreshButtons();
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        if (width < 280 || height < 236) {
            ctx.drawTextWithShadow(textRenderer, "请调小界面缩放后重试（至少 280×236）", 8, 42, 0xFFFFAA);
            super.render(ctx, mouseX, mouseY, delta); return;
        }
        int w = Math.min(330, width - 24), x = (width - w) / 2;
        line(ctx, x, 11, w, "服务器用地 · 系统认购与代管", 0xFFFFFF);
        line(ctx, x, 25, w, "按地图选区登记，不扣款，不自动向公众开放。", 0xCCCCCC);
        line(ctx, x, 66, w, token.isBlank() ? "地图选中 " + LandPick.selected().size() + " 个区块" : previewScope(), 0xDDDDDD);
        line(ctx, x, 81, w, note, 0xFFFF99);
        if (parcel == null) line(ctx, x, 101, w, "暂未读取服务器宗地或没有记录", 0xAAAAAA);
        else {
            String label = LandQuotes.text(parcel, "name", "");
            line(ctx, x, 101, w, (page + 1) + "/" + count + " · " + (label.isBlank() ? "未命名宗地" : label) + " · " + parcel.get("chunks").getAsInt() + " 块", 0xFFFFFF);
            line(ctx, x, 114, w, parcel.get("dimension").getAsString() + " · 区块 " + parcel.get("x").getAsInt() + ", " + parcel.get("z").getAsInt(), 0xCCCCCC);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }
    private String previewScope() {
        if (scope.isEmpty()) return "";
        int minX = scope.stream().mapToInt(ClaimKey::chunkX).min().orElse(0), maxX = scope.stream().mapToInt(ClaimKey::chunkX).max().orElse(0);
        int minZ = scope.stream().mapToInt(ClaimKey::chunkZ).min().orElse(0), maxZ = scope.stream().mapToInt(ClaimKey::chunkZ).max().orElse(0);
        return scope.size() + " 块 · " + scope.get(0).dimension() + " · (" + minX + "," + minZ + ") 至 (" + maxX + "," + maxZ + ")";
    }
    private void line(DrawContext ctx, int x, int y, int w, String value, int color) {
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(value, w), x, y, color);
    }
}
