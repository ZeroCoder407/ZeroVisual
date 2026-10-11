package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Dark loading screen instead of the Mojang red. The logo texture itself is replaced in SplashLogoTextureMixin. */
@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
	@ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int zerovisual$darkBackground(int brandColor) {
		return 0xFF08080C;
	}
}
