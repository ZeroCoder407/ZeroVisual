package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.ScreenshotPlus;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin {
	@Inject(method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;Lnet/minecraft/client/gl/Framebuffer;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
	private static void zerovisual$screenshotPlus(File gameDirectory, @Nullable String fileName, Framebuffer framebuffer,
												  Consumer<Text> messageReceiver, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		// named screenshots come from our own dev tooling, not from F2
		if (client == null || fileName != null) return;
		ScreenshotPlus screenshotPlus = client.modules().get(ScreenshotPlus.class);
		if (screenshotPlus.isEnabled()) screenshotPlus.onScreenshot(gameDirectory);
	}
}
