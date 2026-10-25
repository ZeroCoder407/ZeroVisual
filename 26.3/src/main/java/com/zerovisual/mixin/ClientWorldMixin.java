package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Ambience;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Vector3f;
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
		if (client == null || state.skyColor == null) return;
		Ambience ambience = client.modules().get(Ambience.class);
		if (!ambience.isEnabled()) return;
		int rgb = Math.round(state.skyColor.x() * 255) << 16 | Math.round(state.skyColor.y() * 255) << 8 | Math.round(state.skyColor.z() * 255);
		int tinted = ambience.tintSky(rgb);
		state.skyColor = new Vector3f(((tinted >> 16) & 0xFF) / 255f, ((tinted >> 8) & 0xFF) / 255f, (tinted & 0xFF) / 255f);
	}
}
