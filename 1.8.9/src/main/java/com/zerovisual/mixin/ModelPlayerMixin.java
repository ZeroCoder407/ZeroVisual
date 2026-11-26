package com.zerovisual.mixin;

import com.zerovisual.util.EmoteRig;
import com.zerovisual.util.EmoteRigHolder;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The sleeves, pants and jacket of the player model follow the emote (see ModelBipedMixin). */
@Mixin(ModelPlayer.class)
public class ModelPlayerMixin {
	/** After the vanilla copies of the limb angles into the wear parts: their lower halves bend too. */
	@Inject(method = "setRotationAngles", at = @At("TAIL"))
	private void zerovisual$syncWear(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
									 float scaleFactor, Entity entity, CallbackInfo ci) {
		EmoteRig rig = ((EmoteRigHolder) this).zerovisual$rig();
		if (rig != null) rig.syncWear();
	}

	/** The hips of the pose around the body and the sleeves drawn after it. */
	@Inject(method = "render", at = @At("HEAD"))
	private void zerovisual$hipsStart(Entity entity, float f, float g, float h, float i, float j, float scale, CallbackInfo ci) {
		EmoteRig rig = ((EmoteRigHolder) this).zerovisual$rig();
		if (rig != null) rig.applyHips(scale);
		else GlStateManager.pushMatrix();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void zerovisual$hipsEnd(Entity entity, float f, float g, float h, float i, float j, float scale, CallbackInfo ci) {
		GlStateManager.popMatrix();
	}
}
