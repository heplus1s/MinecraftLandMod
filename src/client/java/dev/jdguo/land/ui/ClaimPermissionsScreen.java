package dev.jdguo.land.ui;

import dev.jdguo.land.claim.ClaimKey;
import dev.jdguo.land.network.ClaimPermissions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.UUID;

/** Explicit single chunk selection avoids silently editing the player's current location. */
public final class ClaimPermissionsScreen extends Screen {
    private final Screen parent;
    private ClaimKey target;
    private boolean requested;
    private UUID pending;
    private boolean editable;
    private int ticks;
    private int scroll;
    private String status = "请选择一个区块；宗地模式请先切换为区块模式";
    private String members = "";
    private TextFieldWidget name;
    private ButtonWidget add, remove, refresh;
    public ClaimPermissionsScreen(Screen parent) { super(Text.literal("区块权限")); this.parent = parent; }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ClaimPermissions.CHANNEL, (client, handler, buf, sender) -> {
            UUID request; boolean editable; String message; String members;
            try {
                request = buf.readUuid(); editable = buf.readBoolean();
                message = buf.readString(256); members = buf.readString(8100);
            } catch (RuntimeException invalid) { return; }
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.player == null) return;
                if (client.currentScreen instanceof ClaimPermissionsScreen screen && request.equals(screen.pending)) {
                    screen.pending = null; screen.editable = editable;
                    screen.status = message; screen.members = members; screen.scroll = 0;
                }
            });
        });
    }
    @Override protected void init() {
        String draft = name == null ? "" : name.getText();
        int w = Math.min(310, width - 24), x = (width - w) / 2;
        if (!requested && LandPick.selected().size() == 1) target = LandPick.selected().iterator().next();
        name = new TextFieldWidget(textRenderer, x, 54, w, 20, Text.literal("玩家名称"));
        name.setMaxLength(16); name.setText(draft); addDrawableChild(name);
        int bw = (w - 8) / 3;
        add = addDrawableChild(ButtonWidget.builder(Text.literal("添加成员"), b -> send(1)).dimensions(x, 80, bw, 20).build());
        remove = addDrawableChild(ButtonWidget.builder(Text.literal("移除成员"), b -> send(2)).dimensions(x + bw + 4, 80, bw, 20).build());
        refresh = addDrawableChild(ButtonWidget.builder(Text.literal("刷新"), b -> send(0)).dimensions(x + 2 * (bw + 4), 80, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close()).dimensions(x, height - 26, w, 20).build());
        if (!requested && target != null) { requested = true; send(0); }
        updateButtons(); setInitialFocus(name);
    }
    private void updateButtons() {
        add.active = remove.active = editable && pending == null && !name.getText().isBlank();
        refresh.active = target != null && pending == null;
    }
    private void send(int action) {
        if (target == null || pending != null) return;
        if (!ClientPlayNetworking.canSend(ClaimPermissions.CHANNEL)) { status = "服务器未支持权限界面；可用 /land trust 和 /land untrust"; return; }
        pending = UUID.randomUUID(); ticks = 0;
        var buf = PacketByteBufs.create(); buf.writeUuid(pending);
        buf.writeString(target.dimension()); buf.writeInt(target.chunkX()); buf.writeInt(target.chunkZ());
        buf.writeByte(action); buf.writeString(name.getText().trim(), 16);
        ClientPlayNetworking.send(ClaimPermissions.CHANNEL, buf);
        status = "正在等待服务器确认"; updateButtons();
    }
    @Override public void tick() {
        name.tick();
        if (pending != null && ++ticks >= 200) { pending = null; status = "未收到确认，请刷新核对后重试"; }
        updateButtons();
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount) * 2); return true;
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        int w = Math.min(310, width - 24), x = (width - w) / 2;
        ctx.drawTextWithShadow(textRenderer, title, x, 14, 0xFFFFFF);
        String location = target == null ? "未选中单个区块" : "区块 " + target.chunkX() + ", " + target.chunkZ();
        ctx.drawTextWithShadow(textRenderer, location + " · 输入玩家名称", x, 36, 0xCCCCCC);
        var lines = textRenderer.wrapLines(Text.literal(status + "\n成员：" + (members.isEmpty() ? "无" : members)), w);
        int count = Math.max(1, (height - 142) / 10);
        scroll = Math.min(scroll, Math.max(0, lines.size() - count));
        ctx.enableScissor(x, 108, x + w, Math.max(109, height - 34));
        for (int i = scroll; i < Math.min(lines.size(), scroll + count); i++) ctx.drawTextWithShadow(textRenderer, lines.get(i), x, 108 + (i - scroll) * 10, 0xFFFFAA);
        ctx.disableScissor();
        super.render(ctx, mouseX, mouseY, delta);
    }
}
