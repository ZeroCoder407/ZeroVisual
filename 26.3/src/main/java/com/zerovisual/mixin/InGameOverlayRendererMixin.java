package com.zerovisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.LowFire;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class InGameOverlayRendererMixin {
	@Inject(method = "submitFire", at = @At("HEAD"))
	private static void zerovisual$lowFirePush(PoseStack matrices, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		matrices.pushPose();
		LowFire lowFire = ZeroVisualClient.get().modules().get(LowFire.class);
		if (lowFire.isEnabled()) matrices.translate(0, -lowFire.offset.getFloat(), 0);
	}

	@Inject(method = "submitFire", at = @At("RETURN"))
	private static void zerovisual$lowFirePop(PoseStack matrices, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		matrices.popPose();
	}
}
