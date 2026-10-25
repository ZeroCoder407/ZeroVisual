package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Nametags;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {
	/** Vanilla only gets here when the name tag should be visible; Nametags draws it later in its own style. */
	@Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("HEAD"), cancellable = true)
	private void zerovisual$nametags(PlayerEntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		Nametags nametags = ZeroVisualClient.get().modules().get(Nametags.class);
		if (nametags.isEnabled() && nametags.capture(state, text)) ci.cancel();
	}
}
