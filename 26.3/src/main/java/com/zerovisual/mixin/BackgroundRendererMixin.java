package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Ambience;
import com.zerovisual.module.impl.visuals.CustomSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class BackgroundRendererMixin {
	@Inject(method = "computeFogColor", at = @At("TAIL"))
	private void zerovisual$fogColor(Camera camera, float partialTicks, ClientLevel level, int renderDistance, float darkenWorldAmount,
									 Vector4f color, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		CustomSky sky = client.modules().get(CustomSky.class);
		if (sky.isActive() && !client.modules().get(Ambience.class).isEnabled() && level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD) {
			float[] fog = sky.fogColor();
			color.set(fog[0], fog[1], fog[2], color.w);
			return;
		}
		Ambience ambience = client.modules().get(Ambience.class);
		if (!ambience.isEnabled()) return;
		float[] tinted = ambience.tintFog(color.x, color.y, color.z);
		color.set(tinted[0], tinted[1], tinted[2], color.w);
	}
}
