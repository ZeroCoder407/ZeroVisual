package com.zerovisual.mixin;

import com.zerovisual.module.impl.cosmetics.Pet;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Idle poses of Pet's Mini me. Applied only to its own render state; the armor models go through
 * the same method, so the armor follows the arms and legs.
 */
@Mixin(BipedEntityModel.class)
public class BipedEntityModelMixin {
	@Shadow
	@Final
	public ModelPart head;
	@Shadow
	@Final
	public ModelPart body;
	@Shadow
	@Final
	public ModelPart rightArm;
	@Shadow
	@Final
	public ModelPart leftArm;
	@Shadow
	@Final
	public ModelPart rightLeg;
	@Shadow
	@Final
	public ModelPart leftLeg;

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("TAIL"))
	private void zerovisual$miniMeIdle(BipedEntityRenderState state, CallbackInfo ci) {
		if (state != Pet.posedState || Pet.poseBlend <= 0.001f) return;
		float k = Pet.poseBlend, t = Pet.poseTime;
		switch (Pet.poseAnimation) {
			case Pet.IDLE_ARMS -> {
				// both arms swing forwards and back together, a little outwards, the head bobs along
				float swing = MathHelper.sin(t * 5.2f);
				rightArm.pitch += (swing * 0.75f - 0.15f) * k;
				leftArm.pitch += (swing * 0.75f - 0.15f) * k;
				rightArm.roll += (0.12f + Math.abs(swing) * 0.1f) * k;
				leftArm.roll -= (0.12f + Math.abs(swing) * 0.1f) * k;
				head.roll += MathHelper.sin(t * 5.2f) * 0.07f * k;
				head.pitch += Math.abs(MathHelper.cos(t * 5.2f)) * 0.06f * k;
			}
			case Pet.IDLE_TAP -> {
				// taps the right foot to a beat: the toes come up and down, the body sways on it
				float beat = Math.max(0, MathHelper.sin(t * 9f));
				rightLeg.pitch += -beat * 0.32f * k;
				rightLeg.roll += 0.06f * k;
				body.roll += MathHelper.sin(t * 4.5f) * 0.035f * k;
				head.roll += MathHelper.sin(t * 4.5f) * 0.06f * k;
				// hands loosely at the sides, swaying with the beat
				rightArm.roll += (0.1f + beat * 0.05f) * k;
				leftArm.roll -= (0.1f + beat * 0.05f) * k;
			}
			case Pet.IDLE_SIT -> {
				// sitting: hands rest on the knees, the feet sway a little
				rightArm.pitch += 0.25f * k;
				leftArm.pitch += 0.25f * k;
				rightLeg.yaw += MathHelper.sin(t * 2f) * 0.08f * k;
				leftLeg.yaw -= MathHelper.sin(t * 2f) * 0.08f * k;
			}
			default -> {
			}
		}
	}
}
