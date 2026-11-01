package com.zerovisual.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.ScreenshotPlus;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

@Mixin(Screenshot.class)
public class ScreenshotRecorderMixin {
	@Inject(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V", at = @At("HEAD"))
	private static void zerovisual$screenshotPlus(File gameDirectory, @Nullable String fileName, RenderTarget framebuffer, int downscale,
												  Consumer<Component> messageReceiver, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		// named screenshots come from our own dev tooling, not from F2
		if (client == null || fileName != null) return;
		ScreenshotPlus screenshotPlus = client.modules().get(ScreenshotPlus.class);
		if (screenshotPlus.isEnabled()) screenshotPlus.onScreenshot(gameDirectory);
	}
}
