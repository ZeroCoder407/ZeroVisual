package com.zerovisual.mixin;

import com.zerovisual.util.EmoteRig;
import com.zerovisual.util.EmoteRigHolder;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During an emote the cape hangs from the chest of the pose, as it does on the newer versions where it is a child of the body. */
@Mixin(LayerCape.class)
public class LayerCapeMixin {
	@Shadow
	@Final
	private RenderPlayer playerRenderer;

	@Inject(method = "doRenderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V", ordinal = 0, shift = At.Shift.AFTER))
	private void zerovisual$onChest(AbstractClientPlayer player, float f, float g, float partialTicks, float h, float i, float j, float scale, CallbackInfo ci) {
		EmoteRig rig = ((EmoteRigHolder) playerRenderer.getMainModel()).zerovisual$rig();
		if (rig != null) rig.toChest(scale);
	}
}
