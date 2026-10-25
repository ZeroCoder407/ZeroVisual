package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.MainMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ZeroVisual wordmark instead of the "MINECRAFT / Java Edition" logo. */
@Mixin(LogoRenderer.class)
public class LogoDrawerMixin {
	@Unique
	private static final Identifier ZEROVISUAL_LOGO = Identifier.fromNamespaceAndPath(ZeroVisualClient.MOD_ID, "textures/gui/title_logo.png");

	@Shadow
	@Final
	private boolean keepLogoThroughFade;

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true)
	private void zerovisual$logo(GuiGraphicsExtractor context, int screenWidth, float alpha, int y, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null || !client.modules().get(MainMenu.class).customLogo.get()) return;
		int color = ARGB.white(keepLogoThroughFade ? 1f : alpha);
		// same footprint as the vanilla logo: 256 wide, texture is 4:1
		context.blit(RenderPipelines.GUI_TEXTURED, ZEROVISUAL_LOGO, screenWidth / 2 - 128, y - 4, 0f, 0f, 256, 64, 256, 64, color);
		ci.cancel();
	}
}
