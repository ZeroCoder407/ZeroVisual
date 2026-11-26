package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.HitColor;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.FloatBuffer;

/** HitColor: the four components of the red hurt tint are the first four values put into the brightness buffer. */
@Mixin(RendererLivingEntity.class)
public class RendererLivingEntityMixin {
	@Redirect(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 0, remap = false))
	private FloatBuffer zerovisual$red(FloatBuffer buffer, float value) {
		return buffer.put(component(16, value));
	}

	@Redirect(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 1, remap = false))
	private FloatBuffer zerovisual$green(FloatBuffer buffer, float value) {
		return buffer.put(component(8, value));
	}

	@Redirect(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 2, remap = false))
	private FloatBuffer zerovisual$blue(FloatBuffer buffer, float value) {
		return buffer.put(component(0, value));
	}

	@Redirect(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 3, remap = false))
	private FloatBuffer zerovisual$alpha(FloatBuffer buffer, float value) {
		return buffer.put(component(24, value));
	}

	/** One byte of the HitColor color as 0..1, or the vanilla value while the module is off. */
	private static float component(int shift, float vanilla) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return vanilla;
		HitColor hitColor = client.modules().get(HitColor.class);
		if (!hitColor.isEnabled()) return vanilla;
		return (hitColor.getOverlayColor() >>> shift & 255) / 255f;
	}
}
