package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.GlintColor;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPhase.class)
public class RenderPhaseMixin {
	/** Used by both glint texturing phases (items and armor/entities). */
	@Inject(method = "setupGlintTexturing", at = @At("TAIL"))
	private static void zerovisual$glintColor(float scale, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		GlintColor glint = client.modules().get(GlintColor.class);
		if (glint.isEnabled()) glint.apply();
	}
}
