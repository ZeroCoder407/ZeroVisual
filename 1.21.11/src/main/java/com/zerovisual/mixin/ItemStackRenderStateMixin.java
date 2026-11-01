package com.zerovisual.mixin;

import com.zerovisual.util.ShieldOverlayHolder;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** ShieldColor: the shield is drawn with its own overlay row instead of the one the caller passes. */
@Mixin(ItemStackRenderState.class)
public class ItemStackRenderStateMixin implements ShieldOverlayHolder {
	@Unique
	private int zerovisual$shieldOverlay = -1;

	@Override
	public void zerovisual$setShieldOverlay(int overlay) {
		zerovisual$shieldOverlay = overlay;
	}

	@ModifyVariable(method = "submit", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int zerovisual$shieldOverlay(int overlayCoords) {
		return zerovisual$shieldOverlay != -1 ? zerovisual$shieldOverlay : overlayCoords;
	}
}
