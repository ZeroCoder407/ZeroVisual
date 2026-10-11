package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.MainMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ZeroVisual wordmark instead of the "MINECRAFT / Java Edition" logo. */
@Mixin(LogoDrawer.class)
public class LogoDrawerMixin {
	@Unique
	private static final Identifier ZEROVISUAL_LOGO = Identifier.of(ZeroVisualClient.MOD_ID, "textures/gui/title_logo.png");

	@Shadow
	@Final
	private boolean ignoreAlpha;

	@Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IFI)V", at = @At("HEAD"), cancellable = true)
	private void zerovisual$logo(DrawContext context, int screenWidth, float alpha, int y, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null || !client.modules().get(MainMenu.class).customLogo.get()) return;
		int color = ColorHelper.getWhite(ignoreAlpha ? 1f : alpha);
		// same footprint as the vanilla logo: 256 wide, texture is 4:1
		context.drawTexture(RenderLayer::getGuiTextured, ZEROVISUAL_LOGO, screenWidth / 2 - 128, y - 4, 0, 0, 256, 64, 256, 64, color);
		ci.cancel();
	}
}
