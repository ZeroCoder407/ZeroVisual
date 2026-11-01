package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.NoWeather;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class WorldMixin {
	@Inject(method = {"getRainLevel", "getThunderLevel"}, at = @At("HEAD"), cancellable = true)
	private void zerovisual$noWeather(float delta, CallbackInfoReturnable<Float> cir) {
		// client world only, never the integrated server
		if (!((Level) (Object) this).isClientSide()) return;
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client != null && client.modules().get(NoWeather.class).isEnabled()) cir.setReturnValue(0f);
	}
}
