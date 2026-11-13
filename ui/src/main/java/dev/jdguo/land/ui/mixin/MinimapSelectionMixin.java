package dev.jdguo.land.ui.mixin;

import dev.jdguo.land.ui.xaero.MiniSelectionHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.common.minimap.highlight.HighlighterRegistry;

@Mixin(value = HighlighterRegistry.class, remap = false)
public class MinimapSelectionMixin {
    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void landui$registerSelection(CallbackInfo ci) {
        HighlighterRegistry registry = (HighlighterRegistry) (Object) this;
        for (AbstractHighlighter highlighter : registry.getHighlighters()) {
            if (highlighter instanceof MiniSelectionHighlighter) {
                return;
            }
        }
        registry.register(new MiniSelectionHighlighter());
    }
}
