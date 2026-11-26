package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Animations;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Animations: the swing speed of your own hand (the attack itself is unchanged). */
@Mixin(EntityLivingBase.class)
public class EntityLivingBaseMixin {
	@Inject(method = "getArmSwingAnimationEnd", at = @At("RETURN"), cancellable = true)
	private void zerovisual$swingSpeed(CallbackInfoReturnable<Integer> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null || (Object) this != Minecraft.getMinecraft().thePlayer) return;
		Animations animations = client.modules().get(Animations.class);
		if (animations.isEnabled()) cir.setReturnValue(animations.adjustSwingDuration(cir.getReturnValue()));
	}
}
