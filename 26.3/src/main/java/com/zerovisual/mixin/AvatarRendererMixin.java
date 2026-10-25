package com.zerovisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Nametags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	/** The game only gets here when the name tag should be visible; Nametags draws its own tag in its place. */
	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD"), cancellable = true)
	private void zerovisual$nametags(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		Nametags nametags = ZeroVisualClient.get().modules().get(Nametags.class);
		if (nametags.isEnabled() && nametags.replace(state, collector)) ci.cancel();
	}
}
