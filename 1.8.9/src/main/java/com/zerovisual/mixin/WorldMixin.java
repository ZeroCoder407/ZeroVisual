package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Ambience;
import com.zerovisual.module.impl.visuals.NoWeather;
import com.zerovisual.module.impl.visuals.TimeChanger;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TimeChanger, NoWeather and the sky of Ambience. World is shared with the integrated server, so only the
 * world of the client is changed.
 */
@Mixin(World.class)
public abstract class WorldMixin {
	@Shadow
	@Final
	public WorldProvider provider;

	private boolean zerovisual$client() {
		return (Object) this instanceof WorldClient && ZeroVisualClient.get() != null;
	}

	@Inject(method = "getCelestialAngle", at = @At("HEAD"), cancellable = true)
	private void zerovisual$time(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if (!zerovisual$client()) return;
		TimeChanger time = ZeroVisualClient.get().modules().get(TimeChanger.class);
		if (time.isEnabled()) cir.setReturnValue(provider.calculateCelestialAngle(time.time.getInt(), partialTicks));
	}

	@Inject(method = "getRainStrength", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noRain(float delta, CallbackInfoReturnable<Float> cir) {
		if (zerovisual$client() && ZeroVisualClient.get().modules().get(NoWeather.class).isEnabled()) cir.setReturnValue(0f);
	}

	@Inject(method = "getThunderStrength", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noThunder(float delta, CallbackInfoReturnable<Float> cir) {
		if (zerovisual$client() && ZeroVisualClient.get().modules().get(NoWeather.class).isEnabled()) cir.setReturnValue(0f);
	}

	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
	private void zerovisual$skyColor(Entity entity, float partialTicks, CallbackInfoReturnable<Vec3> cir) {
		if (!zerovisual$client()) return;
		Ambience ambience = ZeroVisualClient.get().modules().get(Ambience.class);
		if (!ambience.isEnabled()) return;
		Vec3 c = cir.getReturnValue();
		int rgb = (int) (Math.min(1, c.xCoord) * 255) << 16 | (int) (Math.min(1, c.yCoord) * 255) << 8 | (int) (Math.min(1, c.zCoord) * 255);
		int tinted = ambience.tintSky(rgb);
		cir.setReturnValue(new Vec3((tinted >> 16 & 255) / 255.0, (tinted >> 8 & 255) / 255.0, (tinted & 255) / 255.0));
	}
}
