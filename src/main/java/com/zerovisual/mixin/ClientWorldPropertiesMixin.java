package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.TimeChanger;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
public class ClientWorldPropertiesMixin {
	@Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
	private void zerovisual$timeChanger(CallbackInfoReturnable<Long> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		TimeChanger timeChanger = client.modules().get(TimeChanger.class);
		if (timeChanger.isEnabled()) cir.setReturnValue((long) timeChanger.time.getInt());
	}
}
