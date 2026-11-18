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
    private ButtonWidget landui$toggleButton, landui$clearButton, landui$displayButton;
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
        var occupied = new java.util.ArrayList<dev.jdguo.land.ui.MapSelectionPanel.Rect>();
        for (Element child : screen.children()) if (child instanceof ClickableWidget w && w.visible)
            occupied.add(new dev.jdguo.land.ui.MapSelectionPanel.Rect(w.getX(), w.getY(), w.getWidth(), w.getHeight()));
        var panel = dev.jdguo.land.ui.MapSelectionPanel.place(screen.width, screen.height, occupied);
        if (panel == null) return; // K still opens the independent grid on exceptionally cramped screens.
        landui$left = panel.x(); landui$top = panel.y();
        landui$toolbarVisible = true;
        screen.addButton(ButtonWidget.builder(Text.literal("我的领地"), button -> landui$run(() -> MinecraftClient.getInstance().setScreen(new dev.jdguo.land.ui.PersonalLandScreen(screen))))
                .dimensions(landui$left, landui$top + 48, 100, 20).build());
        if (LandQuotes.canManage()) screen.addButton(ButtonWidget.builder(Text.literal("管理员"), button -> {
            if (LandQuotes.canManage()) MinecraftClient.getInstance().setScreen(new LandManageScreen(screen));
        }).dimensions(landui$left + 104, landui$top + 48, 48, 20).build());
        landui$toggleButton = ButtonWidget.builder(Text.literal("选地关"), button -> LandPick.toggle())
                .dimensions(landui$left, landui$top, 48, 20).build();
        screen.addButton(landui$toggleButton);
        screen.addButton(ButtonWidget.builder(Text.literal(LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块"), button -> {
            LandPick.toggleMode();
            button.setMessage(Text.literal(LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块"));
        }).dimensions(landui$left + 52, landui$top, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("购买"), button -> landui$run(() -> LandTradeClient.buy(MinecraftClient.getInstance())))
                .dimensions(landui$left, landui$top + 24, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("出售"), button -> landui$run(() -> LandTradeClient.sell(MinecraftClient.getInstance())))
                .dimensions(landui$left + 52, landui$top + 24, 48, 20).build());
        landui$clearButton = ButtonWidget.builder(Text.literal("清空选区"), button -> {
            LandPick.clear(); LandQuotes.clearNotice();
        }).dimensions(landui$left + 104, landui$top + 24, 48, 20).build();
        screen.addButton(landui$clearButton);
        landui$displayButton = ButtonWidget.builder(Text.literal("隐藏领地"), button -> dev.jdguo.land.client.ClaimDisplayPreferences.toggle())
                .dimensions(landui$left, landui$top + 72, 74, 20).build();
        screen.addButton(landui$displayButton);
        screen.addButton(ButtonWidget.builder(Text.literal("我的配色"), button -> MinecraftClient.getInstance().setScreen(new dev.jdguo.land.ui.OwnerColorScreen(screen)))
                .dimensions(landui$left + 78, landui$top + 72, 74, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("网格"), button -> MinecraftClient.getInstance().setScreen(new LandBoardScreen(screen)))
                .dimensions(landui$left + 104, landui$top, 48, 20).build());
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void landui$pick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!landui$toolbarVisible) return;
        GuiMap screen = (GuiMap) (Object) this;
        var panel = new dev.jdguo.land.ui.MapSelectionPanel.Rect(landui$left, landui$top, 152, 132);
        if (panel.contains(mouseX, mouseY)) {
            // Only actual enabled buttons receive left clicks. All other panel input is consumed.
            boolean enabledButton = false;
            for (Element child : screen.children()) if (child instanceof ClickableWidget widget
                    && widget.visible && widget.active && widget.isMouseOver(mouseX, mouseY)) enabledButton = true;
            if (dev.jdguo.land.ui.MapSelectionPanel.consumePanelClick(button, enabledButton)) cir.setReturnValue(true);
            return;
        }
        if (button != 0 || !LandPick.active()) return;
        for (Element child : screen.children()) if (child.isMouseOver(mouseX, mouseY)) return;
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
        if (!landui$toolbarVisible) return;
        var renderer = MinecraftClient.getInstance().textRenderer;
        int count = LandPick.selected().size();
        landui$toggleButton.setMessage(Text.literal(LandPick.active() ? "选地开" : "选地关"));
        landui$clearButton.active = count > 0;
        landui$displayButton.setMessage(Text.literal(dev.jdguo.land.client.ClaimDisplayPreferences.visible() ? "隐藏领地" : "显示领地"));
        String state = (LandPick.active() ? "选地中" : "选地已关") + " · 已选 " + count + " 块";
        String position = dev.jdguo.land.ui.MapSelectionPanel.selectionLocation(LandPick.selected());
        context.fill(landui$left, landui$top + 94, landui$left + 152, landui$top + 132, 0xDD18202B);
        context.drawTextWithShadow(renderer, renderer.trimToWidth(state, 148), landui$left + 2, landui$top + 96,
                count > 0 ? 0xFFFF70 : 0xFFFFFF);
        context.drawTextWithShadow(renderer, renderer.trimToWidth(position, 148), landui$left + 2, landui$top + 108, 0xBBDFFF);
        context.drawTextWithShadow(renderer, renderer.trimToWidth(LandQuotes.summary(), 148), landui$left + 2, landui$top + 120, 0xFFE7BB);
        if (mouseX >= landui$left && mouseX < landui$left + 152 && mouseY >= landui$top + 94 && mouseY < landui$top + 132) {
            var lines = new java.util.ArrayList<net.minecraft.text.OrderedText>();
            int maxWidth = Math.max(40, Math.min(240, ((GuiMap) (Object) this).width - 24));
            for (String line : java.util.List.of(state, position,
                    "关闭选地不会清空选区；点击「清空选区」可全部取消。",
                    "领地填色按所有者分配；金边为出售中；蓝色为当前选区。",
                    "显示开关只影响本机大/小地图领地层，不隐藏选区或改变交易。", LandQuotes.summary()))
                lines.addAll(renderer.wrapLines(Text.literal(line), maxWidth));
            var fitted = dev.jdguo.land.ui.MapSelectionPanel.limitTooltipLines(lines, ((GuiMap) (Object) this).height,
                    renderer.fontHeight + 1, Text.literal("…内容较长，已省略").asOrderedText());
            context.drawOrderedTooltip(renderer, fitted, mouseX, mouseY);
        }
    }
}
