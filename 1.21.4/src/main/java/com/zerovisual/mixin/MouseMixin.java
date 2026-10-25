package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Zoom;
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

	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void zerovisual$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (window != mc.getWindow().getHandle() || mc.currentScreen != null) return;
		boolean discrete = mc.options.getDiscreteMouseScroll().getValue();
		double amount = (discrete ? Math.signum(vertical) : vertical) * mc.options.getMouseWheelSensitivity().getValue();
		if (ZeroVisualClient.get().modules().get(Zoom.class).onScroll(amount)) ci.cancel();
	}
}
