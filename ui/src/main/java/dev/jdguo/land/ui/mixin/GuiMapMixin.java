package dev.jdguo.land.ui.mixin;

import dev.jdguo.land.ui.LandBoardScreen;
import dev.jdguo.land.ui.LandPick;
import dev.jdguo.land.ui.LandTradeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
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
    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void landui$buttons(CallbackInfo ci) {
        GuiMap screen = (GuiMap) (Object) this;
        screen.addButton(ButtonWidget.builder(Text.literal("领地"), button -> LandPick.toggle())
                .dimensions(6, 6, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal(LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块"), button -> {
            LandPick.toggleMode();
            button.setMessage(Text.literal(LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块"));
        }).dimensions(58, 6, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("购买"), button -> LandTradeClient.buy(MinecraftClient.getInstance()))
                .dimensions(6, 30, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("出售"), button -> LandTradeClient.sell(MinecraftClient.getInstance()))
                .dimensions(58, 30, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("清除"), button -> LandPick.clear())
                .dimensions(110, 30, 48, 20).build());
        screen.addButton(ButtonWidget.builder(Text.literal("网格"), button -> MinecraftClient.getInstance().setScreen(new LandBoardScreen(screen)))
                .dimensions(110, 6, 48, 20).build());
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void landui$pick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!LandPick.active() || button != 0) {
            return;
        }
        GuiMap screen = (GuiMap) (Object) this;
        for (Element child : screen.children()) {
            if (child.isMouseOver(mouseX, mouseY)) {
                return;
            }
        }
        GuiMapAccess access = (GuiMapAccess) screen;
        RegistryKey<World> dimension = access.land$dimension();
        if (dimension == null) {
            return;
        }
        LandPick.click(dimension.getValue().toString(), access.land$blockX() >> 4, access.land$blockZ() >> 4);
        cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = true)
    private void landui$label(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!LandPick.active()) {
            return;
        }
        String mode = LandPick.mode() == LandPick.Mode.PARCEL ? "宗地" : "区块";
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                "点选" + mode + " · 已选 " + LandPick.selected().size() + " 块", 6, 54, 0xFFFFFF);
    }
}
