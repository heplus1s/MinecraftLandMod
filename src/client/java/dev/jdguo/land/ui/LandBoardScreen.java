package dev.jdguo.land.ui;

import dev.jdguo.land.client.ClientClaims;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Chunk grid used when the player is not clicking a Xaero map.
 * Each cell is one 16×16 chunk. This is not a chest and not a bank screen.
 */
public final class LandBoardScreen extends Screen {
    private static final int RADIUS = 6;
    private LandBoardLayout layout;
    private int cell;
    private int originX;
    private int originY;
    private boolean centered;
    private Object gridWorld;

    private final Screen parent;
    private int centerX;
    private int centerZ;
    private String dimension = "minecraft:overworld";

    public LandBoardScreen(Screen parent) {
        super(Text.literal("领地"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!centered && client != null && client.player != null) {
            gridWorld = client.world;
            centerX = client.player.getBlockX() >> 4;
            centerZ = client.player.getBlockZ() >> 4;
            dimension = ClientWorldContext.currentDimension(client);
            if (dimension != null) LandPick.retainDimension(dimension);
            LandQuotes.clearNotice();
            centered = true;
        }
        int span = RADIUS * 2 + 1;
        layout = LandBoardLayout.fit(width, height, span);
        cell = layout.cell();
        originX = layout.x();
        originY = layout.y();
        if (LandQuotes.canManage()) addDrawableChild(ButtonWidget.builder(Text.literal("管理"), button -> {
            if (LandQuotes.canManage()) client.setScreen(new LandManageScreen(this));
        }).dimensions(width / 2 + 30, 16, 44, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("命名"), button -> client.setScreen(new ParcelNameScreen(this)))
                .dimensions(width / 2 - 110, 16, 44, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("权限"), button -> client.setScreen(new ClaimPermissionsScreen(this)))
                .dimensions(width / 2 - 62, 16, 44, 20).build());
        int y = this.height - 48;
        int buttonWidth = Math.min(70, (width - 24) / 3);
        int left = (width - 3 * buttonWidth - 8) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal(LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块"), button -> {
            LandPick.toggleMode();
            button.setMessage(Text.literal(LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块"));
        }).dimensions(left, y, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("购买"), button -> LandTradeClient.buy(client)).dimensions(left + buttonWidth + 4, y, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("出售"), button -> LandTradeClient.sell(client)).dimensions(left + 2 * (buttonWidth + 4), y, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("停售"), button -> LandTradeClient.cancel(client)).dimensions(left, y + 24, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("清除"), button -> LandPick.clear()).dimensions(left + buttonWidth + 4, y + 24, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close()).dimensions(left + 2 * (buttonWidth + 4), y + 24, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("北"), button -> centerZ -= RADIUS).dimensions(this.width / 2 - 12, 18, 24, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("南"), button -> centerZ += RADIUS).dimensions(this.width / 2 - 12, originY + span * cell + 4, 24, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("西"), button -> centerX -= RADIUS).dimensions(originX - 28, originY + span * cell / 2 - 8, 24, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("东"), button -> centerX += RADIUS).dimensions(originX + span * cell + 4, originY + span * cell / 2 - 8, 24, 16).build());
    }

    @Override
    public void tick() {
        if (client != null && gridWorld != client.world) { client.setScreen(null); return; }
        if (client != null) {
            String current = ClientWorldContext.currentDimension(client);
            if (current != null && !current.equals(dimension)) { dimension = current; LandPick.retainDimension(dimension); }
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && pick(mouseX, mouseY)) {
            return true;
        }
        return false;
    }

    private boolean pick(double mouseX, double mouseY) {
        int span = RADIUS * 2 + 1;

        if (!layout.contains(mouseX, mouseY)) return false;
        int col = (int) ((mouseX - originX) / cell);
        int row = (int) ((mouseY - originY) / cell);
        if (col < 0 || row < 0 || col >= span || row >= span) {
            return false;
        }
        String current = ClientWorldContext.currentDimension(client);
        if (current == null) { LandQuotes.notice("正在确认当前服务器世界，请稍后再选择地块。"); return true; }
        if (!current.equals(dimension)) { dimension = current; LandPick.retainDimension(dimension); }
        LandPick.click(dimension, centerX + col - RADIUS, centerZ + row - RADIUS);
        if (ClientWorldContext.supported()) LandPick.requireContext();
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int span = RADIUS * 2 + 1;

        context.drawTextWithShadow(textRenderer, "已选 " + LandPick.selected().size() + " 块", 8, 4, 0xFFFFFF);
        for (int row = 0; row < span; row++) {
            for (int col = 0; col < span; col++) {
                int chunkX = centerX + col - RADIUS;
                int chunkZ = centerZ + row - RADIUS;
                int x = originX + col * cell;
                int y = originY + row * cell;
                context.fill(x, y, x + cell - 1, y + cell - 1, color(chunkX, chunkZ));
            }
        }
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(LandQuotes.summary(), width - 16), 8, this.height - 72, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("中心区块 " + centerX + ", " + centerZ + " · 每格 16×16 方块", width - 16), 8, this.height - 60, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
        if (mouseY >= height - 74 && mouseY <= height - 62) {
            context.drawTooltip(textRenderer, Text.literal(LandQuotes.summary()), mouseX, mouseY);
        }
    }

    private String modeLine() {
        return (LandPick.mode() == LandPick.Mode.PARCEL ? "宗地模式" : "区块模式") + " · 灰色未记录，价格以市场确认为准";
    }

    private int color(int chunkX, int chunkZ) {
        if (dimension == null) return 0xFF2E2E2E;
        if (LandPick.contains(dimension, chunkX, chunkZ)) {
            return 0xFF3D7EFF;
        }
        ClientClaims.Entry entry = ClientClaims.get(dimension, chunkX, chunkZ);
        if (entry == null) {
            return 0xFF2E2E2E;
        }
        if (ClientSales.forSale(dimension, chunkX, chunkZ)) {
            return 0xFFE0C040;
        }
        if (client != null && client.player != null && client.player.getUuid().equals(entry.owner())) {
            return 0xFF2F8F4E;
        }
        return 0xFFD07A2A;
    }
}
