package dev.jdguo.land.mixin.client;

import dev.jdguo.land.client.xaero.WorldMapClaimHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;

/**
 * World Map 1.37.2 builds a highlighter registry while opening a map session,
 * registers its own hooks, then calls {@code end()}, which freezes the list.
 * There is no public event in between, so registration has to happen at the
 * start of {@code end()} while the list is still mutable.
 */
@Mixin(value = HighlighterRegistry.class, remap = false)
public class WorldMapRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void landmod$registerClaims(CallbackInfo ci) {
        HighlighterRegistry registry = (HighlighterRegistry) (Object) this;
        for (AbstractHighlighter highlighter : registry.getHighlighters()) {
            if (highlighter instanceof WorldMapClaimHighlighter) {
                return;
            }
        }
        registry.register(new WorldMapClaimHighlighter());
    }
}
