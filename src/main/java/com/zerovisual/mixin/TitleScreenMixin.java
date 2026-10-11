package com.zerovisual.mixin;

import com.zerovisual.render.MenuBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TitleScreen overrides renderPanoramaBackground, so it needs its own hook (keeps the startup fade-in). */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	@Shadow
	private float backgroundAlpha;

	protected TitleScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
	private void zerovisual$starBackground(DrawContext context, float delta, CallbackInfo ci) {
		if (!MenuBackground.isEnabled()) return;
		MenuBackground.render(context, width, height, backgroundAlpha);
		ci.cancel();
	}
}
