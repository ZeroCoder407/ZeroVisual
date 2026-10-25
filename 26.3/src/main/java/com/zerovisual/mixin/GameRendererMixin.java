package com.zerovisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.MotionBlur;
import com.zerovisual.module.impl.visuals.NoHurtCam;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom and AspectRatio live in CameraMixin, TotemPop in LocalPlayerMixin. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noHurtCam(CameraRenderState cameraState, PoseStack matrices, CallbackInfo ci) {
		if (ZeroVisualClient.get().modules().get(NoHurtCam.class).isEnabled()) ci.cancel();
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;blitEntityOutline()V", shift = At.Shift.AFTER))
	private void zerovisual$motionBlur(CallbackInfo ci) {
		MotionBlur motionBlur = ZeroVisualClient.get().modules().get(MotionBlur.class);
		if (motionBlur.isEnabled()) motionBlur.apply();
	}
}
