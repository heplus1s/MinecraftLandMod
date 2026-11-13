package dev.jdguo.land.mixin;

import dev.jdguo.land.protect.ClaimProtection;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow
    private World world;

    @Inject(method = "collectBlocksAndDamageEntities", at = @At("RETURN"))
    private void landmod$filterClaimedBlocks(CallbackInfo ci) {
        if (!(this.world instanceof ServerWorld serverWorld)) {
            return;
        }
        LivingEntity cause = ((Explosion) (Object) this).getCausingEntity();
        ClaimProtection.filterExplosion(serverWorld, cause, ((Explosion) (Object) this).getAffectedBlocks());
    }
}
