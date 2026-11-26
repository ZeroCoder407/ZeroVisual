package com.zerovisual.mixin;

import com.zerovisual.module.impl.visuals.GlintColor;
import net.minecraft.client.renderer.entity.RenderItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** GlintColor on items: the purple tint constant of the two glint passes. */
@Mixin(RenderItem.class)
public class RenderItemMixin {
	@ModifyConstant(method = "renderEffect", constant = @Constant(intValue = -8372020))
	private int zerovisual$glint(int vanilla) {
		GlintColor glint = GlintColor.active();
		return glint == null ? vanilla : glint.itemTint();
	}
}
