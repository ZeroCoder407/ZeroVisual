package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Zoom;
import com.zerovisual.util.CpsCounter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void zerovisual$countClicks(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (action == InputConstants.PRESS && window == mc.getWindow().handle() && mc.screen == null) {
			CpsCounter.click(button.button());
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void zerovisual$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (window != mc.getWindow().handle() || mc.screen != null) return;
		boolean discrete = mc.options.discreteMouseScroll().get();
		double amount = (discrete ? Math.signum(vertical) : vertical) * mc.options.mouseWheelSensitivity().get();
		if (ZeroVisualClient.get().modules().get(Zoom.class).onScroll(amount)) ci.cancel();
	}

	/** CinematicCamera: the strength of the smoothing. */
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/SmoothDouble;getNewDeltaValue(DD)D"), index = 1)
	private double zerovisual$cinematic(double time) {
		ZeroVisualClient client = ZeroVisualClient.get();
		return client == null ? time : client.modules().get(com.zerovisual.module.impl.visuals.CinematicCamera.class).smoothingTime(time);
	}
}
