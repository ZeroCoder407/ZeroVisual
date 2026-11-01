package com.zerovisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.performance.LiteDrops;
import com.zerovisual.module.impl.visuals.ItemPhysics;
import com.zerovisual.util.ItemPhysicsState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
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
	private RandomSource random;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
	private void zerovisual$captureGround(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
		((ItemPhysicsState) state).zerovisual$setOnGround(entity.onGround());
		((ItemPhysicsState) state).zerovisual$setVelocity((float) entity.getDeltaMovement().x, (float) entity.getDeltaMovement().z);
		// LiteDrops: one model per stack instead of up to five copies
		if (ZeroVisualClient.get().modules().get(LiteDrops.class).isEnabled()) state.count = Math.min(state.count, 1);
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
		at = @At("HEAD"), cancellable = true)
	private void zerovisual$itemPhysics(ItemEntityRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null || !client.modules().get(ItemPhysics.class).isEnabled()) return;
		if (state.item.isEmpty()) return;
		ItemPhysicsState physics = (ItemPhysicsState) state;
		AABB box = state.item.getModelBoundingBox();
		if (!physics.zerovisual$isOnGround()) {
			renderFlying(state, physics, box, matrices, collector);
			ci.cancel();
			return;
		}

		matrices.pushPose();
		// a fixed random yaw per item, so piles do not look identical
		matrices.mulPose(Axis.YP.rotation(state.bobOffset * 2f));
		if (box.getZsize() > 0.0625) {
			// blocks stand on the ground
			matrices.translate(0, (float) -box.minY, 0);
		} else {
			// flat items: lie down (the model depth becomes the height), stacked copies go upwards
			matrices.translate(0, (float) -box.minZ + 0.01f, 0);
			matrices.mulPose(Axis.XP.rotationDegrees(-90));
		}
		ItemEntityRenderer.submitMultipleFromCount(matrices, collector, state.lightCoords, state, random, box);
		matrices.popPose();
		ci.cancel();
	}

	/**
	 * In the air the item somersaults forwards in the direction it flies (a thrown item flies where
	 * the player looked), faster when it moves faster, around its own middle.
	 */
	@Unique
	private void renderFlying(ItemEntityRenderState state, ItemPhysicsState physics, AABB box, PoseStack matrices, SubmitNodeCollector collector) {
		float vx = physics.zerovisual$velocityX(), vz = physics.zerovisual$velocityZ();
		float speed = (float) Math.sqrt(vx * vx + vz * vz);
		// facing the flight direction, or a fixed random yaw when it falls straight down
		float yaw = speed > 0.01f ? (float) Math.atan2(vx, vz) : state.bobOffset * 2f;
		float flip = state.ageInTicks * (12f + Math.min(speed, 1f) * 60f) + state.bobOffset * 57f;
		float cx = (float) ((box.minX + box.maxX) / 2), cy = (float) ((box.minY + box.maxY) / 2), cz = (float) ((box.minZ + box.maxZ) / 2);
		matrices.pushPose();
		matrices.translate(0, cy - (float) box.minY, 0);
		matrices.mulPose(Axis.YP.rotation(yaw));
		matrices.mulPose(Axis.XP.rotationDegrees(flip));
		if (box.getZsize() <= 0.0625) matrices.mulPose(Axis.XP.rotationDegrees(-90));
		matrices.translate(-cx, -cy, -cz);
		ItemEntityRenderer.submitMultipleFromCount(matrices, collector, state.lightCoords, state, random, box);
		matrices.popPose();
	}
}
