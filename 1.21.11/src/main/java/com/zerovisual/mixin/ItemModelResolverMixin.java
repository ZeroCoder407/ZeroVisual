package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.ShieldColor;
import com.zerovisual.util.ShieldOverlayHolder;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The holder of an item is only known here, before rendering: remember the ShieldColor overlay in the render state.
 * GUI items are cached by their model identity, so the tint becomes part of it (a red shield is a different picture).
 */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {
	@Inject(method = "updateForTopItem", at = @At("TAIL"))
	private void zerovisual$shieldColor(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
										Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		int overlay = client == null || owner == null ? -1 : client.modules().get(ShieldColor.class).overlayFor(item, owner.asLivingEntity());
		((ShieldOverlayHolder) output).zerovisual$setShieldOverlay(overlay);
		if (overlay != -1) output.appendModelIdentityElement("zerovisual:shield_overlay/" + overlay);
	}
}
