package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.performance.CrystalOptimizer;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CrystalOptimizer: a still crystal (no spin or bobbing) and no beam. Only how it looks changes. */
@Mixin(EndCrystalEntityRenderer.class)
public class EndCrystalRendererMixin {
	@Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/EndCrystalEntity;Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;F)V", at = @At("TAIL"))
	private void zerovisual$crystal(EndCrystalEntity entity, EndCrystalEntityRenderState state, float tickDelta, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		CrystalOptimizer crystals = client.modules().get(CrystalOptimizer.class);
		if (!crystals.isEnabled()) return;
		if (crystals.still.get()) state.age = 0;
		if (crystals.noBeam.get()) state.beamOffset = null;
	}
}
