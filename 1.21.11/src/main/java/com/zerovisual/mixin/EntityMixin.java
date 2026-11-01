package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
	/** During freelook the mouse turns the camera, not the player. */
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void zerovisual$freelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player) return;
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		Freelook freelook = client.modules().get(Freelook.class);
		if (freelook.isActive()) {
			freelook.rotate(cursorDeltaX, cursorDeltaY);
			ci.cancel();
		}
	}
}
