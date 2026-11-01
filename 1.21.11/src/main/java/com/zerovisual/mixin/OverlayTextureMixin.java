package com.zerovisual.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.HitColor;
import com.zerovisual.module.impl.visuals.ShieldColor;
import com.zerovisual.util.HitColorTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Re-uploads the overlay texture when a color changes: the hurt rows (2..7, vanilla reads row 3) for HitColor and
 * rows 0 and 1, which vanilla never reads, for the ShieldColor ready and broken tints.
 */
@Mixin(OverlayTexture.class)
public class OverlayTextureMixin implements HitColorTexture {
	@Shadow
	@Final
	private DynamicTexture texture;

	@Unique
	private int zerovisual$uploadedColor = HitColor.VANILLA;
	@Unique
	private int zerovisual$uploadedReady = HitColor.VANILLA, zerovisual$uploadedBroken = HitColor.VANILLA;

	@Override
	public void zerovisual$refresh() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		int color = client.modules().get(HitColor.class).getOverlayColor();
		ShieldColor shield = client.modules().get(ShieldColor.class);
		int ready = shield.rowColor(false), broken = shield.rowColor(true);
		if (color == zerovisual$uploadedColor && ready == zerovisual$uploadedReady && broken == zerovisual$uploadedBroken) return;
		zerovisual$uploadedColor = color;
		zerovisual$uploadedReady = ready;
		zerovisual$uploadedBroken = broken;

		NativeImage image = texture.getPixels();
		if (image == null) return;
		for (int u = 0; u < 16; u++) {
			image.setPixel(u, ShieldColor.READY_ROW, ready);
			image.setPixel(u, ShieldColor.BROKEN_ROW, broken);
			for (int v = 2; v < 8; v++) image.setPixel(u, v, color);
		}
		texture.upload();
	}
}
