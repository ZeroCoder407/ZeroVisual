package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Ambience;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ambience tints the vanilla sky color. */
@Mixin(SkyRenderer.class)
public class ClientWorldMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void zerovisual$skyColor(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		Ambience ambience = client.modules().get(Ambience.class);
		if (!ambience.isEnabled()) return;
		state.skyColor = ambience.tintSky(state.skyColor);
	}
}
