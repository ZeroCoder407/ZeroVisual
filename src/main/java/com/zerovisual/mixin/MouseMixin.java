package com.zerovisual.mixin;

import com.zerovisual.util.CpsCounter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
	@Inject(method = "onMouseButton", at = @At("HEAD"))
	private void zerovisual$countClicks(long window, int button, int action, int mods, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (action == GLFW.GLFW_PRESS && window == mc.getWindow().getHandle() && mc.currentScreen == null) {
			CpsCounter.click(button);
		}
	}
}
