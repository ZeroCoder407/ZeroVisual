package com.zerovisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.Animations;
import com.zerovisual.module.impl.visuals.ViewModel;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class HeldItemRendererMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void zerovisual$viewModelPush(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
										  InteractionHand hand, float attack, ItemStack item, float inverseArmHeight, PoseStack matrices,
										  SubmitNodeCollector collector, int light, CallbackInfo ci) {
		matrices.pushPose();
		ViewModel viewModel = ZeroVisualClient.get().modules().get(ViewModel.class);
		if (!viewModel.isEnabled() || playerState.avatarRenderState == null) return;
		HumanoidArm mainArm = playerState.avatarRenderState.mainArm;
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		matrices.translate(viewModel.x.getFloat() * side, viewModel.y.getFloat(), viewModel.z.getFloat());
		float s = viewModel.scale.getFloat();
		matrices.scale(s, s, s);
	}

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void zerovisual$swingAnimation(float animation, PoseStack matrices, int invert, HumanoidArm arm, CallbackInfo ci) {
		Animations animations = ZeroVisualClient.get().modules().get(Animations.class);
		if (!animations.isEnabled() || animations.mode.is("Vanilla")) return;
		animations.applySwing(matrices, animation, invert);
		ci.cancel();
	}

	@ModifyVariable(method = "applyItemArmTransform", at = @At("HEAD"), argsOnly = true)
	private float zerovisual$noEquip(float inverseArmHeight) {
		Animations animations = ZeroVisualClient.get().modules().get(Animations.class);
		if (!animations.isEnabled() || animations.mode.is("Vanilla")) return inverseArmHeight;
		return animations.adjustEquip(inverseArmHeight);
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void zerovisual$viewModelPop(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
										 InteractionHand hand, float attack, ItemStack item, float inverseArmHeight, PoseStack matrices,
										 SubmitNodeCollector collector, int light, CallbackInfo ci) {
		matrices.popPose();
	}
}
