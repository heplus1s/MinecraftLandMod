package dev.jdguo.land.mixin.client;

import dev.jdguo.land.client.xaero.MinimapClaimHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.common.minimap.highlight.HighlighterRegistry;

/**
 * Minimap 23.9.3 seals {@code xaero.common.minimap.highlight.HighlighterRegistry}
 * the same way the world map does. Register before {@code end()} freezes it.
 */
@Mixin(value = HighlighterRegistry.class, remap = false)
public class MinimapRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void landmod$registerClaims(CallbackInfo ci) {
        HighlighterRegistry registry = (HighlighterRegistry) (Object) this;
        for (AbstractHighlighter highlighter : registry.getHighlighters()) {
            if (highlighter instanceof MinimapClaimHighlighter) {
                return;
            }
        }
        registry.register(new MinimapClaimHighlighter());
    }
}
