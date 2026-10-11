package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Animations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	/** Swing speed of the local player's hand (animation only). */
	@ModifyReturnValue(method = "getHandSwingDuration", at = @At("RETURN"))
	private int zerovisual$swingSpeed(int duration) {
		if ((Object) this != MinecraftClient.getInstance().player) return duration;
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return duration;
		Animations animations = client.modules().get(Animations.class);
		return animations.isEnabled() ? animations.adjustSwingDuration(duration) : duration;
	}
}
