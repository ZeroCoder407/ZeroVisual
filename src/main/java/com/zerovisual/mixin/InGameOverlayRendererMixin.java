package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.LowFire;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
	@Inject(method = "renderFireOverlay", at = @At("HEAD"))
	private static void zerovisual$lowFirePush(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		matrices.push();
		LowFire lowFire = ZeroVisualClient.get().modules().get(LowFire.class);
		if (lowFire.isEnabled()) matrices.translate(0, -lowFire.offset.getFloat(), 0);
	}

	@Inject(method = "renderFireOverlay", at = @At("RETURN"))
	private static void zerovisual$lowFirePop(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		matrices.pop();
	}
}
