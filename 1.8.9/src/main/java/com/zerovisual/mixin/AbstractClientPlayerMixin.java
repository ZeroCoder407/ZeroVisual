package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.cosmetics.Cape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cape: your own player wears the ZeroVisual cape. */
@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
	@Inject(method = "getLocationCape", at = @At("HEAD"), cancellable = true)
	private void zerovisual$cape(CallbackInfoReturnable<ResourceLocation> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null || (Object) this != Minecraft.getMinecraft().thePlayer) return;
		if (client.modules().get(Cape.class).isEnabled()) cir.setReturnValue(Cape.TEXTURE);
	}
}
