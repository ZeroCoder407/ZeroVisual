package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.performance.CrystalOptimizer;
import com.zerovisual.module.impl.performance.ParticleLimit;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
	/** CrystalOptimizer: no explosion clouds (crystals, TNT); the explosion itself is unchanged. */
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noExplosions(ParticleOptions options, double x, double y, double z, double xa, double ya, double za,
										 CallbackInfoReturnable<Particle> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		CrystalOptimizer crystals = client.modules().get(CrystalOptimizer.class);
		if (crystals.isEnabled() && crystals.noExplosions.get()
			&& (options.getType() == ParticleTypes.EXPLOSION || options.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
			cir.setReturnValue(null);
		}
	}

	/** ParticleLimit: only a share of all particles is added (block breaking particles come here too). */
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void zerovisual$limit(Particle particle, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		ParticleLimit limit = client.modules().get(ParticleLimit.class);
		if (limit.isEnabled() && limit.skip()) ci.cancel();
	}
}
