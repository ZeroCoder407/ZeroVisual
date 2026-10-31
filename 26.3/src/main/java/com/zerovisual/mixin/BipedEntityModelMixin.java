package com.zerovisual.mixin;

import com.zerovisual.module.impl.cosmetics.Pet;
import com.zerovisual.util.EmoteRig;
import com.zerovisual.util.EmoteRigHolder;
import com.zerovisual.util.EmoteState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Idle poses of Pet's Mini me. Applied only to its own render state; the armor models go through
 * the same method, so the armor follows the arms and legs.
 */
@Mixin(HumanoidModel.class)
public class BipedEntityModelMixin implements EmoteRigHolder {
	@Unique
	private EmoteRig zerovisual$rig;

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

	@Override
	public EmoteRig zerovisual$rig() {
		return zerovisual$rig;
	}

	/** Emotes bend the player model and its armor models; the limbs are only cut once somebody plays one. */
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void zerovisual$emote(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof EmoteState emote)) return;
		float weight = emote.zerovisual$weight();
		if (weight > 0 && zerovisual$rig == null) zerovisual$rig = new EmoteRig((HumanoidModel<?>) (Object) this);
		if (zerovisual$rig == null) return;
		zerovisual$rig.rest();
		if (weight > 0) zerovisual$rig.apply(emote.zerovisual$pose(), weight);
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void zerovisual$miniMeIdle(HumanoidRenderState state, CallbackInfo ci) {
		if (state != Pet.posedState || Pet.poseBlend <= 0.001f) return;
		float k = Pet.poseBlend, t = Pet.poseTime;
		switch (Pet.poseAnimation) {
			case Pet.IDLE_ARMS -> {
				// both arms swing forwards and back together, a little outwards, the head bobs along
				float swing = Mth.sin(t * 5.2f);
				rightArm.xRot += (swing * 0.75f - 0.15f) * k;
				leftArm.xRot += (swing * 0.75f - 0.15f) * k;
				rightArm.zRot += (0.12f + Math.abs(swing) * 0.1f) * k;
				leftArm.zRot -= (0.12f + Math.abs(swing) * 0.1f) * k;
				head.zRot += Mth.sin(t * 5.2f) * 0.07f * k;
				head.xRot += Math.abs(Mth.cos(t * 5.2f)) * 0.06f * k;
			}
			case Pet.IDLE_TAP -> {
				// taps the right foot to a beat: the toes come up and down, the body sways on it
				float beat = Math.max(0, Mth.sin(t * 9f));
				rightLeg.xRot += -beat * 0.32f * k;
				rightLeg.zRot += 0.06f * k;
				body.zRot += Mth.sin(t * 4.5f) * 0.035f * k;
				head.zRot += Mth.sin(t * 4.5f) * 0.06f * k;
				// hands loosely at the sides, swaying with the beat
				rightArm.zRot += (0.1f + beat * 0.05f) * k;
				leftArm.zRot -= (0.1f + beat * 0.05f) * k;
			}
			case Pet.IDLE_SIT -> {
				// sitting: hands rest on the knees, the feet sway a little
				rightArm.xRot += 0.25f * k;
				leftArm.xRot += 0.25f * k;
				rightLeg.yRot += Mth.sin(t * 2f) * 0.08f * k;
				leftLeg.yRot -= Mth.sin(t * 2f) * 0.08f * k;
			}
			default -> {
			}
		}
	}
}
