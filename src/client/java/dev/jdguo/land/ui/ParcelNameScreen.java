package dev.jdguo.land.ui;

import dev.jdguo.land.client.ClientClaims;
import dev.jdguo.land.claim.ParcelNames;
import dev.jdguo.land.network.ParcelRename;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.UUID;

/** Edits the selected parcel, never silently redirects to the parcel underfoot. */
public final class ParcelNameScreen extends Screen {
    private final Screen parent;
    private UUID parcel;
    private UUID pending;
    private TextFieldWidget name;
    private ButtonWidget save;
    private String draft;
    private String status = "";
    private int pendingTicks;

    public ParcelNameScreen(Screen parent) {
        super(Text.literal("宗地命名"));
        this.parent = parent;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ParcelRename.CHANNEL, (client, handler, buf, sender) -> {
            UUID request = buf.readUuid();
            boolean success = buf.readBoolean();
            String message = buf.readString(256);
            client.execute(() -> {
                if (client.currentScreen instanceof ParcelNameScreen screen && request.equals(screen.pending)) {
                    screen.pending = null;
                    screen.status = message;
                    if (success) screen.draft = screen.name.getText();
                }
            });
        });
    }

    @Override protected void init() {
        if (name != null) draft = name.getText();
        int w = Math.max(100, Math.min(280, width - 24));
        int x = (width - w) / 2;
        int y = Math.max(30, height / 2 - 35);
        if (draft == null) {
            ClientClaims.Entry target = selected();
            parcel = target == null ? null : target.parcelId();
            draft = target == null ? "" : target.name();
            status = target == null ? "请先选中自己同一宗地内的区块" : "修改整块宗地；留空可清除名称";
        }
        name = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal("宗地名称"));
        name.setMaxLength(64); // 32 Unicode code points can occupy 64 UTF-16 code units.
        name.setText(draft);
        addDrawableChild(name);
        save = addDrawableChild(ButtonWidget.builder(Text.literal("保存名称"), button -> submit())
                .dimensions(x, y + 32, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(x + w / 2 + 2, y + 32, w / 2 - 2, 20).build());
        name.setEditable(parcel != null && pending == null);
        save.active = parcel != null && pending == null;
        setInitialFocus(name);
    }

    private ClientClaims.Entry selected() {
        if (client == null || client.player == null || LandPick.selected().isEmpty()) return null;
        ClientClaims.Entry target = null;
        for (var key : LandPick.selected()) {
            var entry = ClientClaims.get(key.dimension(), key.chunkX(), key.chunkZ());
            if (entry == null || !client.player.getUuid().equals(entry.owner())) return null;
            if (target != null && !target.parcelId().equals(entry.parcelId())) return null;
            target = entry;
        }
        return target;
    }

    private void submit() {
        if (parcel == null || pending != null) return;
        String cleaned = ParcelNames.clean(name.getText());
        if (cleaned == null) { status = "名称最多32个字符，不能包含控制字符"; return; }
        if (!ClientPlayNetworking.canSend(ParcelRename.CHANNEL)) {
            status = "服务器未支持界面命名，请更新领地模组";
            return;
        }
        pending = UUID.randomUUID();
        pendingTicks = 0;
        var buf = PacketByteBufs.create();
        buf.writeUuid(pending);
        buf.writeUuid(parcel);
        buf.writeString(cleaned, 128);
        ClientPlayNetworking.send(ParcelRename.CHANNEL, buf);
        name.setEditable(false);
        save.active = false;
        status = "正在等待服务器确认";
    }

    @Override public void tick() {
        name.tick();
        if (pending != null && ++pendingTicks >= 200) {
            pending = null;
            status = "未收到确认，请重新打开核对名称后重试";
        }
        name.setEditable(parcel != null && pending == null);
        save.active = parcel != null && pending == null;
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int w = Math.max(100, Math.min(280, width - 24)), x = (width - w) / 2;
        int y = Math.max(30, height / 2 - 35);
        context.drawTextWithShadow(textRenderer, title, x, y - 20, 0xFFFFFF);
        context.drawTextWrapped(textRenderer, Text.literal(status), x, y + 62, w, 0xFFFFAA);
        super.render(context, mouseX, mouseY, delta);
    }
}
