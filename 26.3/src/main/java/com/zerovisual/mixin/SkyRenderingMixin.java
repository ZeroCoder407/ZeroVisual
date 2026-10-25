package com.zerovisual.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.CustomSky;
import com.zerovisual.render.SkyBatch;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * CustomSky replaces the overworld sky: the vanilla dome, sunrise glow, sun, moon, stars and dark lower half
 * are not drawn, the custom sky is drawn in the same render pass instead. The End sky is untouched.
 */
@Mixin(SkyRenderer.class)
public class SkyRenderingMixin {
	@Shadow
	@Final
	private RenderTarget renderTarget;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void zerovisual$sky(GpuBufferSlice skyFog, SkyRenderState state, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null || state.skybox != DimensionType.Skybox.OVERWORLD) return;
		CustomSky sky = client.modules().get(CustomSky.class);
		if (!sky.isActive()) return;
		sky.prepareTextures();
		RenderSystem.setShaderFog(skyFog);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "ZeroVisual sky",
			renderTarget.getColorTextureView(), Optional.empty(), renderTarget.getDepthTextureView(), OptionalDouble.empty())) {
			sky.render(new SkyBatch(pass, RenderSystem.getModelViewMatrixCopy()), state.sunAngle, state.starAngle, 1f - state.rainBrightness);
		}
		ci.cancel();
	}
}
