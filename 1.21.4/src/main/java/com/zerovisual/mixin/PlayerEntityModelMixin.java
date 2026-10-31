package com.zerovisual.mixin;

import com.zerovisual.util.EmoteRig;
import com.zerovisual.util.EmoteRigHolder;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Held items follow the forearm of a bent arm. */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {
	@Inject(method = "setArmAngle", at = @At("TAIL"))
	private void zerovisual$toForearm(Arm arm, MatrixStack matrices, CallbackInfo ci) {
		EmoteRig rig = ((EmoteRigHolder) this).zerovisual$rig();
		if (rig != null) rig.toHand(arm, matrices);
	}
}
