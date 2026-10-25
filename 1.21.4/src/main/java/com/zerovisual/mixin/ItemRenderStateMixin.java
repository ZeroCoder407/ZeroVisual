package com.zerovisual.mixin;

import com.zerovisual.util.ShieldOverlayHolder;
import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** ShieldColor: the shield is drawn with its own overlay row instead of the one the caller passes. */
@Mixin(ItemRenderState.class)
public class ItemRenderStateMixin implements ShieldOverlayHolder {
	@Unique
	private int zerovisual$shieldOverlay = -1;

	@Override
	public void zerovisual$setShieldOverlay(int overlay) {
		zerovisual$shieldOverlay = overlay;
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int zerovisual$shieldOverlay(int overlay) {
		return zerovisual$shieldOverlay != -1 ? zerovisual$shieldOverlay : overlay;
	}
}
