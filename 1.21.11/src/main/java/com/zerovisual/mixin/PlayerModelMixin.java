package com.zerovisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.util.EmoteRig;
import com.zerovisual.util.EmoteRigHolder;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Held items follow the forearm of a bent arm. */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {
	@Inject(method = "translateToHand", at = @At("TAIL"))
	private void zerovisual$toForearm(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
		EmoteRig rig = ((EmoteRigHolder) this).zerovisual$rig();
		if (rig != null) rig.toHand(arm, poseStack);
	}
}
