package com.zerovisual.mixin;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.HitColor;
import com.zerovisual.module.impl.visuals.ShieldColor;
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

/**
 * Re-uploads the overlay texture when a color changes: the hurt rows (2..7, vanilla reads row 3) for HitColor and
 * rows 0 and 1, which vanilla never reads, for the ShieldColor ready and broken tints.
 */
@Mixin(OverlayTexture.class)
public class OverlayTextureMixin {
	@Shadow
	@Final
	private NativeImageBackedTexture texture;

	@Unique
	private int zerovisual$uploadedColor = HitColor.VANILLA;
	@Unique
	private int zerovisual$uploadedReady = HitColor.VANILLA, zerovisual$uploadedBroken = HitColor.VANILLA;

	@Inject(method = "setupOverlayColor", at = @At("HEAD"))
	private void zerovisual$applyHitColor(CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		int color = client.modules().get(HitColor.class).getOverlayColor();
		ShieldColor shield = client.modules().get(ShieldColor.class);
		int ready = shield.rowColor(false), broken = shield.rowColor(true);
		if (color == zerovisual$uploadedColor && ready == zerovisual$uploadedReady && broken == zerovisual$uploadedBroken) return;
		zerovisual$uploadedColor = color;
		zerovisual$uploadedReady = ready;
		zerovisual$uploadedBroken = broken;

		NativeImage image = texture.getImage();
		if (image == null) return;
		for (int u = 0; u < 16; u++) {
			image.setColorArgb(u, ShieldColor.READY_ROW, ready);
			image.setColorArgb(u, ShieldColor.BROKEN_ROW, broken);
			for (int v = 2; v < 8; v++) image.setColorArgb(u, v, color);
		}
		RenderSystem.activeTexture(GlConst.GL_TEXTURE1);
		texture.bindTexture();
		image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), false);
		RenderSystem.activeTexture(GlConst.GL_TEXTURE0);
	}
}
