package dev.jdguo.land.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.trade.LandQuoteChannel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Administrator-only ownership mode; mutation always follows a server-generated preview. */
final class GovernmentLandScreen extends Screen {
    private final Screen parent;
    private final List<ButtonWidget> operations = new ArrayList<>();
    private final List<ButtonWidget> tabButtons = new ArrayList<>();
    private TextFieldWidget name;
    private ButtonWidget confirm, previous, next, locate, rename;
    private String note = "", token = "", action = "", previewAction = "", mergeTarget = "", mergeLabel = "";
    private String previewSummary = "", previewDetails = "", submittedToken = "";
    private boolean retrySave;
    private int requestId, waitTicks, page, count, tab = 1, previewTicks;
    private boolean waiting;
    private JsonObject parcel;
    private List<ClaimKey> scope = List.of();

    GovernmentLandScreen(Screen parent) { super(Text.literal("服务器用地")); this.parent = parent; }

    @Override protected void init() {
        operations.clear(); tabButtons.clear(); confirm = previous = next = locate = rename = null;
        String draft = name == null ? "" : name.getText(); name = null;
        if (width < 280 || height < 236 || !LandQuotes.canManage()) {
            button("返回", 8, 8, 80, this::close); return;
        }
        int w = Math.min(360, width - 24), x = (width - w) / 2;
        int half = (w - 4) / 2, third = (w - 8) / 3;
        button("个人用地", x, 22, half, this::personal);
        button("服务器用地 · 管理员", x + half + 4, 22, w - half - 4, () -> {}).active = false;
        String[] tabs = {"登记", "宗地资料", "处置"};
        for (int i = 0; i < tabs.length; i++) {
            final int selectedTab = i;
            var b = button(tabs[i], x + i * (third + 4), 46, i == 2 ? w - 2 * (third + 4) : third,
                    () -> { if (!waiting) { clearPreview(); tab = selectedTab; clearChildren(); init(); } });
            tabButtons.add(b);
            b.active = i != tab && !waiting;
        }
        if (tab == 0) {
            op("预览认购地图选区", x, 98, w, () -> previewSelection("preview"));
            op("刷新服务器宗地", x, 122, half, () -> read(page));
            op("返回地图选区", x + half + 4, 122, w - half - 4, this::personal);
        } else {
            previous = op("上一宗", x, 98, third, () -> read(page - 1));
            next = op("下一宗", x + third + 4, 98, third, () -> read(page + 1));
            locate = op("地图定位", x + 2 * (third + 4), 98, w - 2 * (third + 4), this::locate);
            if (tab == 1) {
                name = new TextFieldWidget(textRenderer, x, 122, w - 58, 20, Text.literal("宗地新名称"));
                name.setMaxLength(64); name.setText(draft); addDrawableChild(name);
                rename = op("改名", x + w - 54, 122, 54, this::rename);
                op("选中区块权限", x, 146, half, this::permissions);
                op("刷新宗地清单", x + half + 4, 146, w - half - 4, () -> read(page));
            } else {
                op("设合并目标", x, 122, third, this::chooseMergeTarget);
                op("预览合并", x + third + 4, 122, third, this::previewMerge);
                op("释放整宗", x + 2 * (third + 4), 122, w - 2 * (third + 4), () -> previewParcel("previewReleaseParcel"));
                op("拆分选区", x, 146, third, () -> previewSelection("previewSplit"));
                op("释放选区", x + third + 4, 146, third, () -> previewSelection("previewRelease"));
                op("系统挂牌", x + 2 * (third + 4), 146, w - 2 * (third + 4), () -> previewParcel("openListing"));
            }
        }
        confirm = button("请先预览", x, height - 25, half, this::confirm);
        button("取消 / 返回", x + half + 4, height - 25, w - half - 4, this::close);
        if (parcel == null && !waiting && token.isBlank()) read(page);
        refreshButtons();
    }

    private ButtonWidget button(String label, int x, int y, int w, Runnable run) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> run.run()).dimensions(x, y, w, 20).build());
    }
    private ButtonWidget op(String label, int x, int y, int w, Runnable run) {
        var b = button(label, x, y, w, run); operations.add(b); return b;
    }
    private static JsonObject action(String value) {
        var result = new JsonObject(); result.addProperty("action", value); return result;
    }
    private void clearPreview() { token = ""; previewAction = ""; previewSummary = ""; previewDetails = ""; scope = List.of(); previewTicks = 0; submittedToken = ""; retrySave = false; }
    private void read(int nextPage) {
        clearPreview(); var data = action("list"); data.addProperty("page", nextPage); send(data);
    }
    private void previewSelection(String kind) {
        clearPreview(); scope = List.copyOf(LandPick.selected());
        if (scope.isEmpty() || scope.size() > 1024) { note = "先在地图选择 1 到 1024 个同维度区块。"; return; }
        String dimension = scope.get(0).dimension();
        if (scope.stream().anyMatch(key -> !dimension.equals(key.dimension()))) { note = "选区不能跨维度。"; return; }
        var coordinates = new JsonArray();
        for (var key : scope) { coordinates.add(key.chunkX()); coordinates.add(key.chunkZ()); }
        var data = action(kind); data.addProperty("dimension", dimension); data.add("coordinates", coordinates);
        previewAction = kind; send(data);
    }
    private void previewParcel(String kind) {
        if (parcel == null) { note = "请先读取并选择服务器宗地。"; return; }
        clearPreview(); previewAction = kind;
        var data = action(kind); data.addProperty("parcel", parcel.get("id").getAsString()); send(data);
    }
    private void chooseMergeTarget() {
        if (parcel == null) { note = "请先选择目标宗地。"; return; }
        clearPreview(); mergeTarget = parcel.get("id").getAsString(); mergeLabel = LandQuotes.text(parcel, "name", mergeTarget);
        note = "目标已记录，请翻页选择另一宗作为来源，再预览合并。";
    }
    private void previewMerge() {
        if (parcel == null || mergeTarget.isBlank() || mergeTarget.equals(parcel.get("id").getAsString())) {
            note = "先设合并目标，再选另一宗作为来源。"; return;
        }
        clearPreview(); previewAction = "previewMerge";
        var data = action(previewAction); data.addProperty("target", mergeTarget); data.addProperty("source", parcel.get("id").getAsString()); send(data);
    }
    private void confirm() {
        if (waiting || token.isBlank() || !LandQuotes.canManage() || !selectionUnchanged()) return;
        var data = action("confirm"); data.addProperty("token", token);
        submittedToken = token;
        token = ""; // Disable repeat clicks while the same confirmation is in flight.
        send(data);
    }
    private boolean selectionUnchanged() {
        return retrySave || !List.of("preview", "previewSplit", "previewRelease").contains(previewAction) || scope.equals(LandPick.selected());
    }
    private void rename() {
        if (parcel == null || name.getText().isBlank()) return;
        clearPreview(); var data = action("rename"); data.addProperty("page", page);
        data.addProperty("parcel", parcel.get("id").getAsString()); data.addProperty("name", name.getText().trim()); send(data);
    }
    private void personal() {
        clearPreview(); waiting = false; client.setScreen(parent instanceof LandManageScreen manage ? manage.personalScreen() : parent);
    }
    private void locate() {
        if (parcel == null) return;
        String dimension = parcel.get("dimension").getAsString();
        if (!dimension.equals(ClientWorldContext.currentDimension(client))) { note = "请先进入该维度；此操作不会传送。"; return; }
        clearPreview(); client.setScreen(new LandBoardScreen(this, new ClaimKey(dimension, parcel.get("x").getAsInt(), parcel.get("z").getAsInt())));
    }
    private void permissions() {
        if (LandPick.selected().size() != 1) { note = "请在地图中选中一个服务器区块后重开此界面。"; return; }
        clearPreview(); client.setScreen(new ClaimPermissionsScreen(this, LandPick.selected().get(0), true));
    }
    private void send(JsonObject data) {
        if (waiting || !LandQuotes.canManage()) return;
        action = data.get("action").getAsString(); requestId = LandQuotes.nextDimensionManagementSequence();
        waiting = LandQuotes.send(LandQuoteChannel.GOVERNMENT_MANAGE, data, requestId); waitTicks = 0;
        note = waiting ? "等待服务器确认…" : "服务器未支持服务器用地管理。";
        refreshButtons();
    }

    void receive(int generation, JsonObject data) {
        if (!waiting || generation != requestId || !LandQuotes.canManage()) return;
        waiting = false; note = LandQuotes.text(data, "reason", "无响应");
        boolean ok = data.has("ok") && data.get("ok").getAsBoolean();
        if (ok && action.startsWith("preview")) {
            token = LandQuotes.text(data, "token", ""); previewTicks = 0;
            previewSummary = label(previewAction) + " · " + LandQuotes.text(data, "chunks", "?") + " 区块 · "
                    + LandQuotes.text(data, "dimension", "");
            previewDetails = "目标：" + LandQuotes.text(data, "targetName", "") + " " + LandQuotes.text(data, "target", "")
                    + "\n来源：" + LandQuotes.text(data, "sourceName", "") + " " + LandQuotes.text(data, "source", "");
        }
        if ("confirm".equals(action)) {
            if (!ok && data.has("retry") && data.get("retry").getAsBoolean()) {
                token = submittedToken; retrySave = true; previewTicks = 0;
            } else {
                clearPreview();
                if (ok) { String done = note; read(page); note = done; }
            }
        }
        if (ok && data.has("page")) {
            page = data.get("page").getAsInt(); count = data.get("count").getAsInt();
            parcel = data.has("parcel") ? data.getAsJsonObject("parcel") : null;
            if (name != null) name.setText("");
        }
        refreshButtons();
    }
    private static String label(String kind) {
        return switch (kind) {
            case "preview" -> "划入服务器用地";
            case "previewSplit" -> "拆分选区";
            case "previewMerge" -> "合并宗地";
            case "previewRelease", "previewReleaseParcel" -> "释放为无主地";
            default -> "操作";
        };
    }
    private void refreshButtons() {
        if (confirm == null) return;
        boolean allowed = !waiting && LandQuotes.canManage();
        operations.forEach(b -> b.active = allowed);
        for (int i = 0; i < tabButtons.size(); i++) tabButtons.get(i).active = allowed && i != tab;
        confirm.active = allowed && !token.isBlank() && selectionUnchanged();
        confirm.setMessage(Text.literal(token.isBlank() ? "请先预览" : retrySave ? "重试保存（同一操作）" : "确认" + label(previewAction)));
        confirm.setTooltip(Tooltip.of(Text.literal(previewSummary + "\n" + previewDetails + "\n" + note)));
        if (previous != null) previous.active = allowed && page > 0;
        if (next != null) next.active = allowed && page + 1 < count;
        if (locate != null) locate.active = allowed && parcel != null;
        if (rename != null) rename.active = allowed && parcel != null;
        if (name != null) name.setEditable(allowed);
    }
    @Override public void tick() {
        if (!LandQuotes.canManage()) { close(); return; }
        if (name != null) name.tick();
        if (waiting && ++waitTicks >= 200) {
            waiting = false;
            if ("confirm".equals(action) && !submittedToken.isBlank()) {
                token = submittedToken; retrySave = true;
                note = "结果未确认；可手动核验同一操作，不会自动重试。";
            } else { clearPreview(); note = "未收到确认，请刷新核对；不会自动重试。"; }
        }
        if (!retrySave && !token.isBlank() && (!selectionUnchanged() || ++previewTicks >= 1800)) {
            clearPreview(); note = "选区已变化或预览已过期，请重新预览。";
        }
        refreshButtons();
    }
    @Override public void close() { clearPreview(); waiting = false; client.setScreen(parent); }
    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        if (width < 280 || height < 236) {
            ctx.drawTextWithShadow(textRenderer, "请调小界面缩放后重试（至少 280×236）", 8, 42, 0xFFFFAA);
            super.render(ctx, mouseX, mouseY, delta); return;
        }
        if (!LandQuotes.canManage()) { super.render(ctx, mouseX, mouseY, delta); return; }
        int w = Math.min(360, width - 24), x = (width - w) / 2;
        line(ctx, x, 8, w, "用地所有者模式 · 服务器用地仅限管理员", 0xFFFFFF);
        if (tab == 0) {
            line(ctx, x, 70, w, "地图选中 " + LandPick.selected().size() + " 块；仅登记无主地。", 0xDDDDDD);
            line(ctx, x, 82, w, "不扣款；默认不开放公众权限。", 0xDDDDDD);
            line(ctx, x, 146, w, "私人土地需通过市场系统回购，不会强制划入。", 0xCCCCCC);
        } else if (parcel == null) line(ctx, x, 70, w, "暂未读取服务器宗地或没有记录", 0xAAAAAA);
        else {
            String label = LandQuotes.text(parcel, "name", "");
            line(ctx, x, 70, w, (page + 1) + "/" + count + " · " + (label.isBlank() ? "未命名宗地" : label) + " · " + parcel.get("chunks").getAsInt() + " 块", 0xFFFFFF);
            line(ctx, x, 82, w, parcel.get("dimension").getAsString() + " · " + parcel.get("id").getAsString(), 0xCCCCCC);
        }
        line(ctx, x, 170, w, token.isBlank() ? (tab == 2 ? "合并目标：" + (mergeTarget.isBlank() ? "未选择" : mergeLabel) : "预览不会修改所有权；确认后才执行。") : previewSummary, 0xDDDDDD);
        // Two bounded lines preserve the warning area without overlapping the confirmation row.
        String first = textRenderer.trimToWidth(note, w);
        line(ctx, x, 182, w, first, 0xFFFF99);
        line(ctx, x, 194, w, note.substring(first.length()), 0xFFFF99);
        super.render(ctx, mouseX, mouseY, delta);
    }
    private void line(DrawContext ctx, int x, int y, int w, String value, int color) {
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(value, w), x, y, color);
    }
}
