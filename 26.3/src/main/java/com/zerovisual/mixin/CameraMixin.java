package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.AspectRatio;
import com.zerovisual.module.impl.visuals.Freelook;
import com.zerovisual.module.impl.visuals.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
public class CameraMixin {
	/** Uses the freelook angles instead of the player's (the two normal setRotation calls of Camera#alignWithEntity). */
	@WrapOperation(method = "alignWithEntity", at = {
		@At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0),
		@At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1)
	})
	private void zerovisual$freelook(Camera camera, float yaw, float pitch, Operation<Void> original) {
		ZeroVisualClient client = ZeroVisualClient.get();
		Freelook freelook = client != null ? client.modules().get(Freelook.class) : null;
		if (freelook != null && freelook.isActive()) original.call(camera, freelook.getYaw(), freelook.getPitch());
		else original.call(camera, yaw, pitch);
	}

	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float zerovisual$zoom(float fov) {
		return fov / ZeroVisualClient.get().modules().get(Zoom.class).getDivisor();
	}

	/** AspectRatio: the projection width is the height times the wanted ratio (the culling frustum too). */
	@ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"), index = 3)
	private float zerovisual$aspectWidth(float width) {
		AspectRatio aspectRatio = ZeroVisualClient.get().modules().get(AspectRatio.class);
		return aspectRatio.isEnabled() ? Minecraft.getInstance().getWindow().getHeight() * aspectRatio.ratio.getFloat() : width;
	}

	@ModifyArg(method = "createProjectionMatrixForCulling", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFFZ)Lorg/joml/Matrix4f;", remap = false), index = 1)
	private float zerovisual$cullingAspect(float aspect) {
		AspectRatio aspectRatio = ZeroVisualClient.get().modules().get(AspectRatio.class);
		return aspectRatio.isEnabled() ? aspectRatio.ratio.getFloat() : aspect;
	}
}
