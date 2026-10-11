package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
	@Inject(method = "onKey", at = @At("HEAD"))
	private void zerovisual$moduleBinds(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (action != GLFW.GLFW_PRESS || window != mc.getWindow().getHandle() || mc.currentScreen != null) return;
		ZeroVisualClient.get().modules().onKeyPress(key);
	}
}
