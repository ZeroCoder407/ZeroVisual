package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.MainMenu;
import com.zerovisual.render.DrawContext;
import com.zerovisual.render.MenuBackground;
import com.zerovisual.util.BuildVerifier;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The title screen: falling stars instead of the panorama, the ZeroVisual wordmark instead of the Minecraft logo. */
@Mixin(GuiMainMenu.class)
public abstract class GuiMainMenuMixin extends GuiScreen {
	@Unique
	private static final ResourceLocation ZEROVISUAL_LOGO = new ResourceLocation(ZeroVisualClient.MOD_ID, "textures/gui/title_logo.png");

	@Unique
	private static MainMenu zerovisual$settings() {
		ZeroVisualClient client = ZeroVisualClient.get();
		return client == null ? null : client.modules().get(MainMenu.class);
	}

	@Inject(method = "renderSkybox", at = @At("HEAD"), cancellable = true)
	private void zerovisual$starBackground(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		if (!MenuBackground.isEnabled()) return;
		MenuBackground.render(new DrawContext(), width, height, 1f);
		ci.cancel();
	}

	/** The two gradients over the panorama would wash out the star background. */
	@Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;drawGradientRect(IIIIII)V"))
	private void zerovisual$noWash(GuiMainMenu screen, int left, int top, int right, int bottom, int startColor, int endColor) {
		if (!MenuBackground.isEnabled()) ((GuiMainMenuMixin) (Object) screen).drawGradientRect(left, top, right, bottom, startColor, endColor);
	}

	/** The pieces of the vanilla logo are left out while the wordmark is on. */
	@Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;drawTexturedModalRect(IIIIII)V"))
	private void zerovisual$noVanillaLogo(GuiMainMenu screen, int x, int y, int u, int v, int w, int h) {
		MainMenu settings = zerovisual$settings();
		if (settings == null || !settings.customLogo.get()) ((Gui) (Object) screen).drawTexturedModalRect(x, y, u, v, w, h);
	}

	/** The wordmark, where the logo was: 256 wide, the texture is 4:1. */
	@Inject(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V", ordinal = 0))
	private void zerovisual$logo(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		MainMenu settings = zerovisual$settings();
		if (settings == null || !settings.customLogo.get()) return;
		mc.getTextureManager().bindTexture(ZEROVISUAL_LOGO);
		GlStateManager.enableBlend();
		GlStateManager.color(1, 1, 1, 1);
		Gui.drawModalRectWithCustomSizedTexture(width / 2 - 128, 30 - 4, 0, 0, 256, 64, 256, 64);
	}

	/** Modified builds say so on the title screen (see BuildVerifier). */
	@Inject(method = "drawScreen", at = @At("TAIL"))
	private void zerovisual$unofficial(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		BuildVerifier.renderTitleWarning(new DrawContext(), width, height);
	}
}
