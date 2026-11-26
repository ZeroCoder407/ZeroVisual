package com.zerovisual.mixin;

import com.zerovisual.emote.EmoteManager;
import com.zerovisual.emote.EmotePose;
import com.zerovisual.util.EmoteRig;
import com.zerovisual.util.EmoteRigHolder;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes bend the player model and its armor: 1.8.9 passes the entity to setRotationAngles, so every model
 * samples the pose of its player itself. The limbs are only cut once somebody plays an emote.
 */
@Mixin(ModelBiped.class)
public class ModelBipedMixin implements EmoteRigHolder {
	@Unique
	private EmoteRig zerovisual$rig;
	@Unique
	private final EmotePose zerovisual$pose = new EmotePose();

	@Override
	public EmoteRig zerovisual$rig() {
		return zerovisual$rig;
	}

	@Inject(method = "setRotationAngles", at = @At("TAIL"))
	private void zerovisual$emote(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
								  float scaleFactor, Entity entity, CallbackInfo ci) {
		if (!(entity instanceof EntityPlayer)) return;
		float weight = EmoteManager.sample(entity.getUniqueID(), zerovisual$pose);
		if (weight > 0 && zerovisual$rig == null) zerovisual$rig = new EmoteRig((ModelBiped) (Object) this);
		if (zerovisual$rig == null) return;
		zerovisual$rig.rest();
		if (weight > 0) zerovisual$rig.apply(zerovisual$pose, weight);
	}

	/** Mini me of Pet sits like riding. */
	@Inject(method = "setRotationAngles", at = @At("HEAD"))
	private void zerovisual$miniMeSit(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
									  float scaleFactor, Entity entity, CallbackInfo ci) {
		ModelBiped model = (ModelBiped) (Object) this;
		// Mini me shares the model of the player: what it changes must not stay for the player, whose first person
		// arm does not set these again (1.8.9 never resets the Z angles of the head, body and legs)
		model.bipedHead.rotateAngleZ = 0;
		model.bipedBody.rotateAngleZ = 0;
		model.bipedRightLeg.rotateAngleZ = 0;
		model.bipedLeftLeg.rotateAngleZ = 0;
		if (entity != null && entity == com.zerovisual.module.impl.cosmetics.Pet.posedState) model.isRiding = com.zerovisual.module.impl.cosmetics.Pet.posedSitting;
		else if (entity != null) model.isRiding = entity.isRiding();
	}

	/** The idle poses of Mini me on top of the walk; the armor models go through here too, so the armor follows. */
	@Inject(method = "setRotationAngles", at = @At("TAIL"))
	private void zerovisual$miniMeIdle(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
									   float scaleFactor, Entity entity, CallbackInfo ci) {
		if (entity == null || entity != com.zerovisual.module.impl.cosmetics.Pet.posedState || com.zerovisual.module.impl.cosmetics.Pet.poseBlend <= 0.001f) return;
		ModelBiped model = (ModelBiped) (Object) this;
		float k = com.zerovisual.module.impl.cosmetics.Pet.poseBlend, t = com.zerovisual.module.impl.cosmetics.Pet.poseTime;
		switch (com.zerovisual.module.impl.cosmetics.Pet.poseAnimation) {
			case com.zerovisual.module.impl.cosmetics.Pet.IDLE_ARMS: {
				// both arms swing forwards and back together, a little outwards, the head bobs along
				float swing = net.minecraft.util.MathHelper.sin(t * 5.2f);
				model.bipedRightArm.rotateAngleX += (swing * 0.75f - 0.15f) * k;
				model.bipedLeftArm.rotateAngleX += (swing * 0.75f - 0.15f) * k;
				model.bipedRightArm.rotateAngleZ += (0.12f + Math.abs(swing) * 0.1f) * k;
				model.bipedLeftArm.rotateAngleZ -= (0.12f + Math.abs(swing) * 0.1f) * k;
				model.bipedHead.rotateAngleZ += net.minecraft.util.MathHelper.sin(t * 5.2f) * 0.07f * k;
				model.bipedHead.rotateAngleX += Math.abs(net.minecraft.util.MathHelper.cos(t * 5.2f)) * 0.06f * k;
				break;
			}
			case com.zerovisual.module.impl.cosmetics.Pet.IDLE_TAP: {
				// taps the right foot to a beat: the toes come up and down, the body sways on it
				float beat = Math.max(0, net.minecraft.util.MathHelper.sin(t * 9f));
				model.bipedRightLeg.rotateAngleX += -beat * 0.32f * k;
				model.bipedRightLeg.rotateAngleZ += 0.06f * k;
				model.bipedBody.rotateAngleZ += net.minecraft.util.MathHelper.sin(t * 4.5f) * 0.035f * k;
				model.bipedHead.rotateAngleZ += net.minecraft.util.MathHelper.sin(t * 4.5f) * 0.06f * k;
				// hands loosely at the sides, swaying with the beat
				model.bipedRightArm.rotateAngleZ += (0.1f + beat * 0.05f) * k;
				model.bipedLeftArm.rotateAngleZ -= (0.1f + beat * 0.05f) * k;
				break;
			}
			case com.zerovisual.module.impl.cosmetics.Pet.IDLE_SIT: {
				// sitting: hands rest on the knees, the feet sway a little
				model.bipedRightArm.rotateAngleX += 0.25f * k;
				model.bipedLeftArm.rotateAngleX += 0.25f * k;
				model.bipedRightLeg.rotateAngleY += net.minecraft.util.MathHelper.sin(t * 2f) * 0.08f * k;
				model.bipedLeftLeg.rotateAngleY -= net.minecraft.util.MathHelper.sin(t * 2f) * 0.08f * k;
				break;
			}
			default:
				break;
		}
		ModelBiped.copyModelAngles(model.bipedHead, model.bipedHeadwear);
	}

	/** The hips of the pose around the whole model; ModelPlayer does this itself, its sleeves are drawn after. */
	@Inject(method = "render", at = @At("HEAD"))
	private void zerovisual$hipsStart(Entity entity, float f, float g, float h, float i, float j, float scale, CallbackInfo ci) {
		if ((Object) this instanceof ModelPlayer) return;
		if (zerovisual$rig != null) zerovisual$rig.applyHips(scale);
		else GlStateManager.pushMatrix();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void zerovisual$hipsEnd(Entity entity, float f, float g, float h, float i, float j, float scale, CallbackInfo ci) {
		if ((Object) this instanceof ModelPlayer) return;
		GlStateManager.popMatrix();
	}

	/** Held items follow the forearm of a bent arm. */
	@Inject(method = "postRenderArm", at = @At("TAIL"))
	private void zerovisual$toForearm(float scale, CallbackInfo ci) {
		if (zerovisual$rig != null) zerovisual$rig.toHand(true, scale);
	}
}
