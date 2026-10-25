package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.TimeChanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TimeChanger: the client-side world clocks show the chosen time (sun, moon, sky colors and stars follow them). */
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
public class ClientWorldPropertiesMixin {
	@Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
	private void zerovisual$timeChanger(CallbackInfoReturnable<Long> cir) {
		TimeChanger timeChanger = zerovisual$timeChanger();
		if (timeChanger != null) cir.setReturnValue((long) timeChanger.time.getInt());
	}

	@Inject(method = "partialTick", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noPartialTick(CallbackInfoReturnable<Float> cir) {
		if (zerovisual$timeChanger() != null) cir.setReturnValue(0f);
	}

	@org.spongepowered.asm.mixin.Unique
	private static TimeChanger zerovisual$timeChanger() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return null;
		TimeChanger timeChanger = client.modules().get(TimeChanger.class);
		return timeChanger.isEnabled() ? timeChanger : null;
	}
}
