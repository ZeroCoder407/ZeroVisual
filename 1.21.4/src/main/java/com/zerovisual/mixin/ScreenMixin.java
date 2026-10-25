package com.zerovisual.mixin;

import com.zerovisual.render.MenuBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menu screens outside a world: star background instead of the panorama. */
@Mixin(Screen.class)
public class ScreenMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;

	@Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
	private void zerovisual$starBackground(DrawContext context, float delta, CallbackInfo ci) {
		if (!MenuBackground.isEnabled()) return;
		MenuBackground.render(context, width, height, 1f);
		ci.cancel();
	}
}
