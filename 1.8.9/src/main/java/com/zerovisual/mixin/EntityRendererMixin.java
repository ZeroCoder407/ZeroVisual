package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.AspectRatio;
import com.zerovisual.module.impl.visuals.CinematicCamera;
import com.zerovisual.module.impl.visuals.Freelook;
import com.zerovisual.module.impl.visuals.NoHurtCam;
import com.zerovisual.module.impl.visuals.Zoom;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom, AspectRatio, NoHurtCam, CinematicCamera and the camera of Freelook. */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
	private void zerovisual$zoom(float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client != null) cir.setReturnValue(cir.getReturnValue() / client.modules().get(Zoom.class).getDivisor());
	}

	/** Every projection of the world: the camera, the sky and terrain passes, the clouds and the hand. */
	@ModifyArg(method = {"setupCameraTransform", "renderWorldPass", "renderCloudsCheck", "renderHand"},
		at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false), index = 1)
	private float zerovisual$aspectRatio(float aspect) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return aspect;
		AspectRatio aspectRatio = client.modules().get(AspectRatio.class);
		return aspectRatio.isEnabled() ? aspectRatio.ratio.getFloat() : aspect;
	}

	@Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noHurtCam(float partialTicks, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client != null && client.modules().get(NoHurtCam.class).isEnabled()) ci.cancel();
	}

	/** CinematicCamera: the strength of the smoothing. */
	@ModifyArg(method = "updateRenderer", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MouseFilter;smooth(FF)F"), index = 1)
	private float zerovisual$cinematic(float time) {
		ZeroVisualClient client = ZeroVisualClient.get();
		return client == null ? time : (float) client.modules().get(CinematicCamera.class).smoothingTime(time);
	}

	/** Freelook: the mouse turns the camera instead of the player. */
	@Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;setAngles(FF)V"))
	private void zerovisual$freelookMouse(EntityPlayerSP player, float yaw, float pitch) {
		Freelook freelook = freelook();
		// Entity#setAngles turns the pitch the other way
		if (freelook != null && freelook.isActive()) freelook.rotate(yaw, -pitch);
		else player.setAngles(yaw, pitch);
	}

	@Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F"))
	private float zerovisual$freelookYaw(Entity entity) {
		Freelook freelook = freelook();
		return freelook != null && freelook.isActive() ? freelook.getYaw() : entity.rotationYaw;
	}

	@Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F"))
	private float zerovisual$freelookPrevYaw(Entity entity) {
		Freelook freelook = freelook();
		return freelook != null && freelook.isActive() ? freelook.getYaw() : entity.prevRotationYaw;
	}

	@Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F"))
	private float zerovisual$freelookPitch(Entity entity) {
		Freelook freelook = freelook();
		return freelook != null && freelook.isActive() ? freelook.getPitch() : entity.rotationPitch;
	}

	@Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F"))
	private float zerovisual$freelookPrevPitch(Entity entity) {
		Freelook freelook = freelook();
		return freelook != null && freelook.isActive() ? freelook.getPitch() : entity.prevRotationPitch;
	}

	private static Freelook freelook() {
		ZeroVisualClient client = ZeroVisualClient.get();
		return client == null ? null : client.modules().get(Freelook.class);
	}
}
