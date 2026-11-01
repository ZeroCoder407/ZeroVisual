package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.FullBright;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public class LightmapTextureManagerMixin {
	// ordinal 0 is the darkness effect scale, ordinal 1 is the gamma option
	@ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
	private float zerovisual$fullBright(float gamma) {
		FullBright fullBright = ZeroVisualClient.get().modules().get(FullBright.class);
		return fullBright.isEnabled() ? fullBright.brightness.getFloat() : gamma;
	}
}
