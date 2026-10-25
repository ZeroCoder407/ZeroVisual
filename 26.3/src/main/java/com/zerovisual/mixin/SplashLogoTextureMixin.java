package com.zerovisual.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.zerovisual.ZeroVisualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The loading screen logo is read from the vanilla jar before mod resources exist,
 * so the ZeroVisual version is read straight from our jar.
 * splash.png uses the vanilla layout: left half of the wordmark on top, right half below.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.LoadingOverlay$LogoTexture")
public class SplashLogoTextureMixin {
	@Inject(method = "loadContents", at = @At("HEAD"), cancellable = true)
	private void zerovisual$logo(ResourceManager resourceManager, CallbackInfoReturnable<TextureContents> cir) {
		try (InputStream stream = SplashLogoTextureMixin.class.getResourceAsStream("/assets/zerovisual/textures/gui/splash.png")) {
			if (stream == null) return;
			cir.setReturnValue(new TextureContents(NativeImage.read(stream), new TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0.0F)));
		} catch (Exception e) {
			ZeroVisualClient.LOGGER.warn("Failed to load the ZeroVisual splash logo, using the vanilla one", e);
		}
	}
}
