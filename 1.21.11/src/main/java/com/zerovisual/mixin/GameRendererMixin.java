package com.zerovisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.MotionBlur;
import com.zerovisual.module.impl.visuals.NoHurtCam;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.zerovisual.module.impl.visuals.AspectRatio;
import com.zerovisual.module.impl.visuals.Zoom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom and AspectRatio live in CameraMixin, TotemPop in LocalPlayerMixin. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float zerovisual$zoom(float fov) {
		return fov / ZeroVisualClient.get().modules().get(Zoom.class).getDivisor();
	}

	/** AspectRatio: the world (and the culling frustum) is drawn with the wanted ratio. */
	@ModifyArg(method = "getProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;", remap = false), index = 1)
	private float zerovisual$aspect(float aspect) {
		AspectRatio aspectRatio = ZeroVisualClient.get().modules().get(AspectRatio.class);
		return aspectRatio.isEnabled() ? aspectRatio.ratio.getFloat() : aspect;
	}

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noHurtCam(com.mojang.blaze3d.vertex.PoseStack matrices, float partialTicks, CallbackInfo ci) {
		if (ZeroVisualClient.get().modules().get(NoHurtCam.class).isEnabled()) ci.cancel();
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void zerovisual$motionBlur(CallbackInfo ci) {
		MotionBlur motionBlur = ZeroVisualClient.get().modules().get(MotionBlur.class);
		if (motionBlur.isEnabled()) motionBlur.apply();
	}
}
