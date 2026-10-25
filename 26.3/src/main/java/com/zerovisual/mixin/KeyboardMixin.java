package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void zerovisual$moduleBinds(long window, int action, KeyEvent event, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (action != InputConstants.PRESS || window != mc.getWindow().handle() || mc.gui.screen() != null) return;
		ZeroVisualClient.get().modules().onKeyPress(event.key());
	}
}
