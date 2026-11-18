package dev.jdguo.land.ui.mixin;

import dev.jdguo.land.ui.LandBoardScreen;
import dev.jdguo.land.ui.LandManageScreen;
import dev.jdguo.land.ui.LandQuotes;
import dev.jdguo.land.ui.LandPick;
import dev.jdguo.land.ui.LandTradeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;

/**
 * Adds the land button to Xaero's world map and lets a click pick 16×16 chunks.
 */
@Mixin(value = GuiMap.class, remap = false)
public class GuiMapMixin {
    private int landui$left;
    private int landui$top;
    private boolean landui$toolbarVisible;
    private dev.jdguo.land.ui.xaero.CurrentMapDimension.View landui$view;
    private boolean landui$checkView() {
        var now = dev.jdguo.land.ui.xaero.CurrentMapDimension.view(((GuiMap) (Object) this).getMapProcessor());
        if (now == null || !now.equals(landui$view)) {
            LandPick.clear(); landui$view = now; return false;
        }
        return true;
    }
    private void landui$run(Runnable action) {
        if (!landui$checkView()) return;
        var processor = ((GuiMap) (Object) this).getMapProcessor();
        var map = processor.getMapWorld();
        var dimension = map == null ? null : map.getCurrentDimension();
        String resolved = dimension == null ? null : dev.jdguo.land.ui.xaero.CurrentMapDimension.resolve(dimension.getDimId(), processor);
        if (resolved == null || LandPick.selected().stream().anyMatch(key -> !key.dimension().equals(resolved))) {
            LandPick.clear();
            LandQuotes.notice("当前显示的地图不是已确认的当前世界自动地图，已清除旧选区，未提交交易。");
            return;
        }
        action.run();
    }
    @Inject(method = "render", at = @At("HEAD"), remap = true)
    private void landui$viewChanged(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        landui$checkView();
    }
    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void landui$buttons(CallbackInfo ci) {
        GuiMap screen = (GuiMap) (Object) this;
        landui$toolbarVisible = false;
        // Reserve one compact toolbar away from Xaero's corner controls. Never move its widgets.
        landui$left = Math.max(6, Math.min(screen.width - 158, screen.width / 2 - 24));
        landui$top = 32;
        while (landui$top + 96 < screen.height - 32 && landui$blocked(screen, landui$left, landui$top)) landui$top += 24;
        if (landui$blocked(screen, landui$left, landui$top)) return; // K still opens the independent grid.
        landui$toolbarVisible = true;
        screen.addButton(ButtonWidget.builder(Text.literal("命名"), button -> landui$run(() -> MinecraftClient.getInstance().setScreen(new dev.jdguo.land.ui.ParcelNameScreen(screen))))
                .dimensions(landui$left, landui$top + 48, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("权限"), button -> landui$run(() -> MinecraftClient.getInstance().setScreen(new dev.jdguo.land.ui.ClaimPermissionsScreen(screen))))
                .dimensions(landui$left + 52, landui$top + 48, 48, 20).build());
        if (LandQuotes.canManage()) screen.addButton(ButtonWidget.builder(Text.literal("管理"), button -> {
            if (LandQuotes.canManage()) MinecraftClient.getInstance().setScreen(new LandManageScreen(screen));
        }).dimensions(landui$left + 104, landui$top + 48, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("领地"), button -> LandPick.toggle())
                .dimensions(landui$left, landui$top, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal(LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块"), button -> {
            LandPick.toggleMode();
            button.setMessage(Text.literal(LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块"));
        }).dimensions(landui$left + 52, landui$top, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("购买"), button -> landui$run(() -> LandTradeClient.buy(MinecraftClient.getInstance())))
                .dimensions(landui$left, landui$top + 24, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("出售"), button -> landui$run(() -> LandTradeClient.sell(MinecraftClient.getInstance())))
                .dimensions(landui$left + 52, landui$top + 24, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("清除"), button -> LandPick.clear())
                .dimensions(landui$left + 104, landui$top + 24, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("网格"), button -> MinecraftClient.getInstance().setScreen(new LandBoardScreen(screen)))
                .dimensions(landui$left + 104, landui$top, 48, 20).build());
    }

    private static boolean landui$blocked(GuiMap screen, int x, int y) {
        for (Element child : screen.children()) {
            if (child instanceof ClickableWidget widget && widget.visible
                    && x < widget.getX() + widget.getWidth() && x + 152 > widget.getX()
                    && y < widget.getY() + widget.getHeight() && y + 96 > widget.getY()) return true;
        }
        return y + 96 > screen.height - 32;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void landui$pick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!landui$toolbarVisible || !LandPick.active() || button != 0) {
            return;
        }
        GuiMap screen = (GuiMap) (Object) this;
        for (Element child : screen.children()) {
            if (child.isMouseOver(mouseX, mouseY)) {
                return;
            }
        }
        landui$checkView();
        GuiMapAccess access = (GuiMapAccess) screen;
        RegistryKey<World> dimension = access.land$dimension();
        if (dimension == null) {
            return;
        }
        String resolved = dev.jdguo.land.ui.xaero.CurrentMapDimension.resolve(dimension, screen.getMapProcessor());
        if (resolved == null) {
            LandPick.clear();
            LandQuotes.notice(dev.jdguo.land.ui.ClientLandDimensions.rejectionMessage(MinecraftClient.getInstance(), dimension.getValue().toString()));
            cir.setReturnValue(true);
            return;
        }
        LandPick.click(resolved, access.land$blockX() >> 4, access.land$blockZ() >> 4);
        if (dev.jdguo.land.ui.ClientWorldContext.supported()
                && resolved.equals(dev.jdguo.land.ui.ClientWorldContext.currentDimension(MinecraftClient.getInstance()))) LandPick.requireContext();
        cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = true)
    private void landui$label(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!landui$toolbarVisible || !LandPick.active()) {
            return;
        }
        String mode = LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块";
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                "点选" + mode + " · 已选 " + LandPick.selected().size() + " 块", landui$left, landui$top + 72, 0xFFFFFF);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                MinecraftClient.getInstance().textRenderer.trimToWidth(LandQuotes.summary(), 152), landui$left, landui$top + 84, 0xFFE7BB);
        if (mouseX >= landui$left && mouseX < landui$left + 152 && mouseY >= landui$top + 82 && mouseY <= landui$top + 94) {
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.literal(LandQuotes.summary()), mouseX, mouseY);
        }
    }
}
