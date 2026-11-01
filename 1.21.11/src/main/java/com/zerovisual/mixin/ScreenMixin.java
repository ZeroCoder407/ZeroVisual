package com.zerovisual.mixin;

import com.zerovisual.render.MenuBackground;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menu screens outside a world (the title screen too): star background instead of the panorama. */
@Mixin(Screen.class)
public class ScreenMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;

	@Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
	private void zerovisual$starBackground(GuiGraphics context, float delta, CallbackInfo ci) {
		if (!MenuBackground.isEnabled()) return;
		MenuBackground.render(context, width, height, 1f);
		ci.cancel();
	}
}
