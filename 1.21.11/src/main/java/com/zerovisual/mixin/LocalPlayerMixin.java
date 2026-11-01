package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.cosmetics.TotemPop;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class LocalPlayerMixin {
	/** TotemPop draws its own animation instead of the floating totem. */
	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void zerovisual$totemPop(ItemStack stack, CallbackInfo ci) {
		if (stack.has(DataComponents.DEATH_PROTECTION) && ZeroVisualClient.get().modules().get(TotemPop.class).replacesFloatingItem()) ci.cancel();
	}
}
