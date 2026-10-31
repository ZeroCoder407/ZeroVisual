package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.emote.EmoteManager;
import com.zerovisual.module.impl.visuals.Nametags;
import com.zerovisual.util.EmoteState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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

	/** Copies the emote pose of the player into the render state, like everything else the model needs. */
	@Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
	private void zerovisual$emotePose(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
		EmoteState emote = (EmoteState) state;
		emote.zerovisual$setWeight(EmoteManager.sample(player.getUuid(), emote.zerovisual$pose()));
		// a dance needs empty hands: what is held would only hide the moves
		if (emote.zerovisual$weight() > 0) {
			state.rightHandItemState.clear();
			state.leftHandItemState.clear();
		}
	}
}
