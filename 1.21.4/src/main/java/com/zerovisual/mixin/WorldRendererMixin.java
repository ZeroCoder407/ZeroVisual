package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.CustomSky;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noCloudsInSpace(CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client != null && client.modules().get(CustomSky.class).hidesClouds()) ci.cancel();
	}
}
