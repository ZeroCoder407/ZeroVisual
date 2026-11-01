package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.TimeChanger;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TimeChanger: the client-side time of day is the chosen one (sun, moon, sky colors and stars follow it). */
@Mixin(ClientLevel.ClientLevelData.class)
public class ClientWorldPropertiesMixin {
	@Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
	private void zerovisual$timeChanger(CallbackInfoReturnable<Long> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		TimeChanger timeChanger = client.modules().get(TimeChanger.class);
		if (timeChanger.isEnabled()) cir.setReturnValue((long) timeChanger.time.getInt());
	}
}
