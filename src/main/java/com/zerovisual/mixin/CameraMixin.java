package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Freelook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class CameraMixin {
	/** Uses the freelook angles instead of the player's (the two normal setRotation calls of Camera#update). */
	@WrapOperation(method = "update", at = {
		@At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0),
		@At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 1)
	})
	private void zerovisual$freelook(Camera camera, float yaw, float pitch, Operation<Void> original) {
		ZeroVisualClient client = ZeroVisualClient.get();
		Freelook freelook = client != null ? client.modules().get(Freelook.class) : null;
		if (freelook != null && freelook.isActive()) original.call(camera, freelook.getYaw(), freelook.getPitch());
		else original.call(camera, yaw, pitch);
	}
}
