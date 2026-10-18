package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.ItemPhysics;
import com.zerovisual.util.ItemPhysicsState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dropped items lie flat on the ground instead of floating and spinning, and somersault while they fly. */
@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
	@Shadow
	@Final
	private Random random;

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
	private void zerovisual$captureGround(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
		((ItemPhysicsState) state).zerovisual$setOnGround(entity.isOnGround());
		((ItemPhysicsState) state).zerovisual$setVelocity((float) entity.getVelocity().x, (float) entity.getVelocity().z);
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("HEAD"), cancellable = true)
	private void zerovisual$itemPhysics(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null || !client.modules().get(ItemPhysics.class).isEnabled()) return;
		if (state.itemRenderState.isEmpty()) return;
		ItemPhysicsState physics = (ItemPhysicsState) state;
		if (!physics.zerovisual$isOnGround()) {
			renderFlying(state, physics, matrices, vertexConsumers, light);
			ci.cancel();
			return;
		}

		matrices.push();
		// a fixed random yaw per item, so piles do not look identical
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation(state.uniqueOffset * 2f));
		float scaleY = state.itemRenderState.getTransformation().scale.y();
		if (state.itemRenderState.hasDepth()) {
			// blocks stand on the ground
			// same height as the lowest point of the vanilla bobbing
			matrices.translate(0, 0.25f * scaleY, 0);
		} else {
			// flat items: lie down, stacked copies go upwards
			matrices.translate(0, 0.02f, 0);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
		}
		ItemEntityRenderer.renderStack(matrices, vertexConsumers, light, state, random);
		matrices.pop();
		ci.cancel();
	}

	/**
	 * In the air the item somersaults forwards in the direction it flies (a thrown item flies where
	 * the player looked), faster when it moves faster, around its own middle.
	 */
	@Unique
	private void renderFlying(ItemEntityRenderState state, ItemPhysicsState physics, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		float vx = physics.zerovisual$velocityX(), vz = physics.zerovisual$velocityZ();
		float speed = (float) Math.sqrt(vx * vx + vz * vz);
		// facing the flight direction, or a fixed random yaw when it falls straight down
		float yaw = speed > 0.01f ? (float) Math.atan2(vx, vz) : state.uniqueOffset * 2f;
		float flip = state.age * (12f + Math.min(speed, 1f) * 60f) + state.uniqueOffset * 57f;
		float scaleY = state.itemRenderState.getTransformation().scale.y();
		matrices.push();
		matrices.translate(0, 0.25f * scaleY, 0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(flip));
		if (!state.itemRenderState.hasDepth()) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
		matrices.translate(0, -0.25f * scaleY, 0);
		ItemEntityRenderer.renderStack(matrices, vertexConsumers, light, state, random);
		matrices.pop();
	}
}
