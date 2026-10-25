package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.performance.CrystalOptimizer;
import com.zerovisual.module.impl.performance.ParticleLimit;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
	/** CrystalOptimizer: no explosion clouds (crystals, TNT); the explosion itself is unchanged. */
	@Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noExplosions(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz,
										 CallbackInfoReturnable<Particle> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		CrystalOptimizer crystals = client.modules().get(CrystalOptimizer.class);
		if (crystals.isEnabled() && crystals.noExplosions.get()
			&& (effect.getType() == ParticleTypes.EXPLOSION || effect.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
			cir.setReturnValue(null);
		}
	}

	/** ParticleLimit: only a share of all particles is added (block breaking particles come here too). */
	@Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
	private void zerovisual$limit(Particle particle, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		ParticleLimit limit = client.modules().get(ParticleLimit.class);
		if (limit.isEnabled() && limit.skip()) ci.cancel();
	}
}
