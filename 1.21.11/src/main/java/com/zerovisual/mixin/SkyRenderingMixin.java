package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.CustomSky;
import com.zerovisual.render.SkyBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CustomSky replaces the overworld sky: the first call of the overworld pass, the sky dome, draws the custom sky in a
 * pass of its own, and the sunrise glow, sun, moon, stars and dark lower half are skipped. The End sky is untouched.
 */
@Mixin(SkyRenderer.class)
public class SkyRenderingMixin {
	@Unique
	private static float zerovisual$sunAngle, zerovisual$starAngle, zerovisual$rain;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void zerovisual$angles(net.minecraft.client.multiplayer.ClientLevel level, float partialTicks, net.minecraft.client.Camera camera, SkyRenderState state, CallbackInfo ci) {
		zerovisual$sunAngle = state.sunAngle;
		zerovisual$starAngle = state.starAngle;
		zerovisual$rain = 1f - state.rainBrightness;
	}

	@Unique
	private static CustomSky zerovisual$sky() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return null;
		CustomSky sky = client.modules().get(CustomSky.class);
		return sky.isActive() ? sky : null;
	}

	@Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
	private void zerovisual$disc(int color, CallbackInfo ci) {
		CustomSky sky = zerovisual$sky();
		if (sky == null) return;
		sky.prepareTextures();
		RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
		sky.render(new SkyBatch(target, new Matrix4f(RenderSystem.getModelViewMatrix())), zerovisual$sunAngle, zerovisual$starAngle, zerovisual$rain);
		ci.cancel();
	}

	@Inject(method = {"renderSunriseAndSunset", "renderSunMoonAndStars"}, at = @At("HEAD"), cancellable = true)
	private void zerovisual$skipSun(CallbackInfo ci) {
		if (zerovisual$sky() != null) ci.cancel();
	}

	@Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
	private void zerovisual$skipDark(CallbackInfo ci) {
		if (zerovisual$sky() != null) ci.cancel();
	}
}
