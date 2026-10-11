package com.zerovisual.mixin;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.HitColor;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-uploads the hurt rows (v < 8) of the overlay texture when the HitColor color changes. */
@Mixin(OverlayTexture.class)
public class OverlayTextureMixin {
	@Shadow
	@Final
	private NativeImageBackedTexture texture;

	@Unique
	private int zerovisual$uploadedColor = HitColor.VANILLA;

	@Inject(method = "setupOverlayColor", at = @At("HEAD"))
	private void zerovisual$applyHitColor(CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		int color = client.modules().get(HitColor.class).getOverlayColor();
		if (color == zerovisual$uploadedColor) return;
		zerovisual$uploadedColor = color;

		NativeImage image = texture.getImage();
		if (image == null) return;
		for (int v = 0; v < 8; v++) {
			for (int u = 0; u < 16; u++) image.setColorArgb(u, v, color);
		}
		RenderSystem.activeTexture(GlConst.GL_TEXTURE1);
		texture.bindTexture();
		image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), false);
		RenderSystem.activeTexture(GlConst.GL_TEXTURE0);
	}
}
