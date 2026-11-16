package dev.jdguo.land.ui.mixin;

import dev.jdguo.land.ui.xaero.WorldSelectionHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;

@Mixin(value = HighlighterRegistry.class, remap = false)
public class WorldMapSelectionMixin {
    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void landui$registerSelection(CallbackInfo ci) {
        HighlighterRegistry registry = (HighlighterRegistry) (Object) this;
        for (AbstractHighlighter highlighter : registry.getHighlighters()) {
            if (highlighter instanceof WorldSelectionHighlighter) {
                return;
            }
        }
        registry.register(new WorldSelectionHighlighter());
    }
}
