package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.performance.CrystalOptimizer;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CrystalOptimizer: a still crystal (no spin or bobbing) and no beam. Only how it looks changes. */
@Mixin(EndCrystalRenderer.class)
public class EndCrystalRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V", at = @At("TAIL"))
	private void zerovisual$crystal(EndCrystal entity, EndCrystalRenderState state, float partialTicks, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		CrystalOptimizer crystals = client.modules().get(CrystalOptimizer.class);
		if (!crystals.isEnabled()) return;
		if (crystals.still.get()) state.ageInTicks = 0;
		if (crystals.noBeam.get()) state.beamOffset = null;
	}
}
