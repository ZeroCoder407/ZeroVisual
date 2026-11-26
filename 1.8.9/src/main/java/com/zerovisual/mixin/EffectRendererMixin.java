package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.performance.CrystalOptimizer;
import com.zerovisual.module.impl.performance.ParticleLimit;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.EnumParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ParticleLimit and the explosion clouds of CrystalOptimizer. */
@Mixin(EffectRenderer.class)
public class EffectRendererMixin {
	@Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
	private void zerovisual$limit(EntityFX effect, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		ParticleLimit limit = client.modules().get(ParticleLimit.class);
		if (limit.isEnabled() && limit.skip()) ci.cancel();
	}

	@Inject(method = "spawnEffectParticle", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noExplosions(int particleId, double x, double y, double z, double dx, double dy, double dz, int[] parameters,
										 CallbackInfoReturnable<EntityFX> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		CrystalOptimizer crystals = client.modules().get(CrystalOptimizer.class);
		if (!crystals.isEnabled() || !crystals.noExplosions.get()) return;
		if (particleId == EnumParticleTypes.EXPLOSION_HUGE.getParticleID() || particleId == EnumParticleTypes.EXPLOSION_LARGE.getParticleID()) {
			cir.setReturnValue(null);
		}
	}
}
