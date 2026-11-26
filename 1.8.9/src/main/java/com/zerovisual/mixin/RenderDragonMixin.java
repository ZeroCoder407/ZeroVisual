package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.performance.CrystalOptimizer;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.entity.boss.EntityDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CrystalOptimizer: no healing beams between the crystals and the dragon. */
@Mixin(RenderDragon.class)
public class RenderDragonMixin {
	@Inject(method = "drawRechargeRay", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noBeam(EntityDragon dragon, double x, double y, double z, float partialTicks, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		CrystalOptimizer crystals = client.modules().get(CrystalOptimizer.class);
		if (crystals.isEnabled() && crystals.noBeam.get()) ci.cancel();
	}
}
