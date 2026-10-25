package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Ambience;
import com.zerovisual.module.impl.visuals.CustomSky;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.BackgroundRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
	@ModifyReturnValue(method = "getFogColor", at = @At("RETURN"))
	private static Vector4f zerovisual$fogColor(Vector4f color) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return color;
		CustomSky sky = client.modules().get(CustomSky.class);
		if (sky.isActive() && !client.modules().get(Ambience.class).isEnabled() && MinecraftClient.getInstance().world.getDimensionEffects().getSkyType() == DimensionEffects.SkyType.NORMAL) {
			float[] fog = sky.fogColor();
			return new Vector4f(fog[0], fog[1], fog[2], color.w);
		}
		Ambience ambience = client.modules().get(Ambience.class);
		if (!ambience.isEnabled()) return color;
		float[] tinted = ambience.tintFog(color.x, color.y, color.z);
		return new Vector4f(tinted[0], tinted[1], tinted[2], color.w);
	}
}
