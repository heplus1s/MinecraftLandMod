package dev.jdguo.land.ui.mixin;

import dev.jdguo.land.ui.LandBoardScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.gui.GuiMinimapMain;

/**
 * Opens the land chunk grid from the minimap settings screen.
 */
@Mixin(value = GuiMinimapMain.class, remap = false)
public class GuiMinimapMainMixin {
    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void landui$button(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        ((ScreenInvoker) screen).landui$addDrawableChild(ButtonWidget.builder(Text.literal("领地"), button ->
                MinecraftClient.getInstance().setScreen(new LandBoardScreen(screen)))
                .dimensions(6, 6, 48, 20).build());
    }
}
