package com.zerovisual.mixin;

import com.zerovisual.emote.EmoteManager;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A dance needs empty hands: what is held would only hide the moves. */
@Mixin(LayerHeldItem.class)
public class LayerHeldItemMixin {
	@Inject(method = "doRenderLayer", at = @At("HEAD"), cancellable = true)
	private void zerovisual$emptyHands(EntityLivingBase entity, float f, float g, float partialTicks, float h, float i, float j, float scale,
									   CallbackInfo ci) {
		if (entity instanceof EntityPlayer && EmoteManager.current(entity.getUniqueID()) != null) ci.cancel();
	}
}
