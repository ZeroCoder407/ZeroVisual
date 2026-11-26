package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Animations;
import com.zerovisual.module.impl.visuals.LowFire;
import com.zerovisual.module.impl.visuals.ViewModel;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LowFire, ViewModel and the swing styles of Animations. */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
	@Inject(method = "renderFireInFirstPerson", at = @At("HEAD"))
	private void zerovisual$lowFireStart(float partialTicks, CallbackInfo ci) {
		GlStateManager.pushMatrix();
		LowFire lowFire = ZeroVisualClient.get().modules().get(LowFire.class);
		if (lowFire.isEnabled()) GlStateManager.translate(0, -lowFire.offset.getFloat(), 0);
	}

	@Inject(method = "renderFireInFirstPerson", at = @At("RETURN"))
	private void zerovisual$lowFireEnd(float partialTicks, CallbackInfo ci) {
		GlStateManager.popMatrix();
	}

	/**
	 * Places the held item: ViewModel moves and scales it first (as the newer versions do before the swing), then the
	 * Animations style replaces the vanilla swing. 1.8.9 only draws the right hand.
	 */
	@Inject(method = "transformFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void zerovisual$placeItem(float equipProgress, float swingProgress, CallbackInfo ci) {
		ViewModel viewModel = ZeroVisualClient.get().modules().get(ViewModel.class);
		if (viewModel.isEnabled()) {
			GlStateManager.translate(viewModel.x.getFloat(), viewModel.y.getFloat(), viewModel.z.getFloat());
			float s = viewModel.scale.getFloat();
			GlStateManager.scale(s, s, s);
		}
		Animations animations = ZeroVisualClient.get().modules().get(Animations.class);
		if (!animations.isEnabled() || animations.mode.is("Vanilla")) return;
		animations.applySwing(swingProgress, equipProgress, 1);
		// the styles end facing like the newer versions, whose item model holds the 45 degrees that 1.8.9 turns here
		GlStateManager.rotate(45, 0, 1, 0);
		GlStateManager.scale(0.4f, 0.4f, 0.4f);
		ci.cancel();
	}

	/** The swing styles carry their own movement: the vanilla bob before the placement is left out. */
	@Inject(method = "doItemUsedTransformations", at = @At("HEAD"), cancellable = true)
	private void zerovisual$noBob(float swingProgress, CallbackInfo ci) {
		Animations animations = ZeroVisualClient.get().modules().get(Animations.class);
		if (animations.isEnabled() && !animations.mode.is("Vanilla")) ci.cancel();
	}
}
