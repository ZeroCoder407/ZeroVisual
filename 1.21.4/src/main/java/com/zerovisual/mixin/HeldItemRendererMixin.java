package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Animations;
import com.zerovisual.module.impl.visuals.ViewModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
	private void zerovisual$viewModelPush(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
										  ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
										  int light, CallbackInfo ci) {
		matrices.push();
		ViewModel viewModel = ZeroVisualClient.get().modules().get(ViewModel.class);
		if (!viewModel.isEnabled()) return;
		Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == Arm.RIGHT ? 1f : -1f;
		matrices.translate(viewModel.x.getFloat() * side, viewModel.y.getFloat(), viewModel.z.getFloat());
		float s = viewModel.scale.getFloat();
		matrices.scale(s, s, s);
	}

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void zerovisual$swingAnimation(float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm, CallbackInfo ci) {
		Animations animations = ZeroVisualClient.get().modules().get(Animations.class);
		if (!animations.isEnabled() || animations.mode.is("Vanilla")) return;
		animations.applySwing(matrices, swingProgress, equipProgress, armX);
		ci.cancel();
	}

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
	private void zerovisual$viewModelPop(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
										 ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
										 int light, CallbackInfo ci) {
		matrices.pop();
	}
}
