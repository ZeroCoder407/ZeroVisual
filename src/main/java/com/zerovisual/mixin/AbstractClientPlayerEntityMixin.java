package com.zerovisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.cosmetics.Cape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayerEntity.class)
public class AbstractClientPlayerEntityMixin {
	@ModifyReturnValue(method = "getSkinTextures", at = @At("RETURN"))
	private SkinTextures zerovisual$cape(SkinTextures textures) {
		if ((Object) this != MinecraftClient.getInstance().player) return textures;
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return textures;
		Cape cape = client.modules().get(Cape.class);
		return cape.isEnabled() ? cape.apply(textures) : textures;
	}
}
