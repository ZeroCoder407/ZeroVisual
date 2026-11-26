package com.zerovisual.mixin;

import com.zerovisual.module.impl.visuals.GlintColor;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** GlintColor on armor: the second color call of renderGlint is the purple tint (0.5, 0.25, 0.8) times a flicker. */
@Mixin(LayerArmorBase.class)
public class LayerArmorBaseMixin {
	@Redirect(method = "renderGlint", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", ordinal = 1))
	private void zerovisual$glint(float r, float g, float b, float a) {
		GlintColor glint = GlintColor.active();
		if (glint == null) {
			GlStateManager.color(r, g, b, a);
			return;
		}
		float[] c = glint.armorTint(r / 0.5f);
		GlStateManager.color(c[0], c[1], c[2], a);
	}
}
