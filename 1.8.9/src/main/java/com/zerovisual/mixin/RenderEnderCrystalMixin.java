package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.performance.CrystalOptimizer;
import net.minecraft.client.renderer.tileentity.RenderEnderCrystal;
import net.minecraft.entity.item.EntityEnderCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** CrystalOptimizer: crystals stand still. */
@Mixin(RenderEnderCrystal.class)
public class RenderEnderCrystalMixin {
	@Redirect(method = "doRender(Lnet/minecraft/entity/item/EntityEnderCrystal;DDDFF)V",
		at = @At(value = "FIELD", target = "Lnet/minecraft/entity/item/EntityEnderCrystal;innerRotation:I"))
	private int zerovisual$still(EntityEnderCrystal crystal) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return crystal.innerRotation;
		CrystalOptimizer crystals = client.modules().get(CrystalOptimizer.class);
		return crystals.isEnabled() && crystals.still.get() ? 0 : crystal.innerRotation;
	}
}
