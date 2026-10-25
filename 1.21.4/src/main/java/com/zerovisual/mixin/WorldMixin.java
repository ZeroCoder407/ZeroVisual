package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.NoWeather;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldMixin {
	@Inject(method = {"getRainGradient", "getThunderGradient"}, at = @At("HEAD"), cancellable = true)
	private void zerovisual$noWeather(float delta, CallbackInfoReturnable<Float> cir) {
		// client world only, never the integrated server
		if (!((World) (Object) this).isClient()) return;
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client != null && client.modules().get(NoWeather.class).isEnabled()) cir.setReturnValue(0f);
	}
}
