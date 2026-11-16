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
    private static final int CELL = 14;

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
        if (client != null && client.player != null) {
            centerX = client.player.getBlockX() >> 4;
            centerZ = client.player.getBlockZ() >> 4;
            dimension = client.player.getWorld().getRegistryKey().getValue().toString();
        }
        int y = this.height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal(LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块"), button -> {
            LandPick.toggleMode();
            button.setMessage(Text.literal(LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块"));
        }).dimensions(8, y, 50, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("购买"), button -> LandTradeClient.buy(client)).dimensions(62, y, 50, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("出售"), button -> LandTradeClient.sell(client)).dimensions(116, y, 50, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("停售"), button -> LandTradeClient.cancel(client)).dimensions(170, y, 50, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("清除"), button -> LandPick.clear()).dimensions(224, y, 50, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close()).dimensions(this.width - 58, y, 50, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("北"), button -> centerZ -= RADIUS).dimensions(this.width / 2 - 12, 18, 24, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("南"), button -> centerZ += RADIUS).dimensions(this.width / 2 - 12, this.height - 48, 24, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("西"), button -> centerX -= RADIUS).dimensions(8, 80, 24, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("东"), button -> centerX += RADIUS).dimensions(this.width - 32, 80, 24, 16).build());
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && pick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean pick(double mouseX, double mouseY) {
        int span = RADIUS * 2 + 1;
        int originX = (this.width - span * CELL) / 2;
        int originY = 40;
        int col = (int) ((mouseX - originX) / CELL);
        int row = (int) ((mouseY - originY) / CELL);
        if (col < 0 || row < 0 || col >= span || row >= span) {
            return false;
        }
        LandPick.click(dimension, centerX + col - RADIUS, centerZ + row - RADIUS);
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        int span = RADIUS * 2 + 1;
        int originX = (this.width - span * CELL) / 2;
        int originY = 40;
        context.drawTextWithShadow(textRenderer, "已选 " + LandPick.selected().size() + " 块", 8, 4, 0xFFFFFF);
        for (int row = 0; row < span; row++) {
            for (int col = 0; col < span; col++) {
                int chunkX = centerX + col - RADIUS;
                int chunkZ = centerZ + row - RADIUS;
                int x = originX + col * CELL;
                int y = originY + row * CELL;
                context.fill(x, y, x + CELL - 1, y + CELL - 1, color(chunkX, chunkZ));
            }
        }
        context.drawTextWithShadow(textRenderer, modeLine(), 8, this.height - 44, 0xDDDDDD);
    }

    private String modeLine() {
        return (LandPick.mode() == LandPick.Mode.PARCEL ? "宗地模式" : "区块模式") + " · 黄色为在售";
    }

    private int color(int chunkX, int chunkZ) {
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
