package com.zerovisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.emote.EmoteManager;
import com.zerovisual.module.impl.visuals.Nametags;
import com.zerovisual.util.EmoteState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	/** The game only gets here when the name tag should be visible; Nametags draws its own tag in its place. */
	@Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At("HEAD"), cancellable = true)
	private void zerovisual$nametags(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		Nametags nametags = ZeroVisualClient.get().modules().get(Nametags.class);
		if (nametags.isEnabled() && nametags.replace(state, collector)) ci.cancel();
	}

	/** Copies the emote pose of the player into the render state, like everything else the model needs. */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void zerovisual$emotePose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		EmoteState emote = (EmoteState) state;
		emote.zerovisual$setWeight(EmoteManager.sample(entity.getUUID(), emote.zerovisual$pose()));
		// a dance needs empty hands: what is held would only hide the moves
		if (emote.zerovisual$weight() > 0) {
			state.rightHandItemState.clear();
			state.leftHandItemState.clear();
		}
	}
}
