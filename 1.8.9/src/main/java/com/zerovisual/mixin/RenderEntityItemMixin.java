package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.performance.LiteDrops;
import com.zerovisual.module.impl.visuals.ItemPhysics;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ItemPhysics (dropped items lie flat on the ground and somersault while they fly) and LiteDrops (one copy per
 * stack). func_177077_a places the item and returns how many copies are drawn; func_177078_a is that number.
 */
@Mixin(RenderEntityItem.class)
public abstract class RenderEntityItemMixin {
	@Shadow
	protected abstract int func_177078_a(ItemStack stack);

	@Inject(method = "func_177078_a", at = @At("HEAD"), cancellable = true)
	private void zerovisual$liteDrops(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client != null && client.modules().get(LiteDrops.class).isEnabled()) cir.setReturnValue(1);
	}

	@Inject(method = "func_177077_a", at = @At("HEAD"), cancellable = true)
	private void zerovisual$itemPhysics(EntityItem item, double x, double y, double z, float partialTicks, IBakedModel model,
										CallbackInfoReturnable<Integer> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null || !client.modules().get(ItemPhysics.class).isEnabled()) return;
		ItemStack stack = item.getEntityItem();
		if (stack == null || stack.getItem() == null) return;
		boolean block = model.isGui3d();
		float scaleY = model.getItemCameraTransforms().getTransform(ItemCameraTransforms.TransformType.GROUND).scale.y;
		// a fixed random yaw per item, so piles do not look identical
		float yaw = (float) Math.toDegrees(item.hoverStart * 2f);
		if (item.onGround) {
			if (block) {
				// blocks stand on the ground, at the lowest point of the vanilla bobbing
				GlStateManager.translate((float) x, (float) y + 0.25f * scaleY, (float) z);
				GlStateManager.rotate(yaw, 0, 1, 0);
			} else {
				// flat items lie down; stacked copies go upwards
				GlStateManager.translate((float) x, (float) y + 0.02f, (float) z);
				GlStateManager.rotate(yaw, 0, 1, 0);
				GlStateManager.rotate(-90, 1, 0, 0);
			}
		} else {
			// in the air the item somersaults forwards in the direction it flies, faster when it moves faster
			float vx = (float) item.motionX, vz = (float) item.motionZ;
			float speed = (float) Math.sqrt(vx * vx + vz * vz);
			float facing = speed > 0.01f ? (float) Math.toDegrees(Math.atan2(vx, vz)) : yaw;
			float flip = (item.getAge() + partialTicks) * (12f + Math.min(speed, 1f) * 60f) + item.hoverStart * 57f;
			GlStateManager.translate((float) x, (float) y + 0.25f * scaleY, (float) z);
			GlStateManager.rotate(facing, 0, 1, 0);
			GlStateManager.rotate(flip, 1, 0, 0);
			if (!block) GlStateManager.rotate(-90, 1, 0, 0);
			GlStateManager.translate(0, -0.25f * scaleY, 0);
		}
		GlStateManager.color(1, 1, 1, 1);
		cir.setReturnValue(func_177078_a(stack));
	}
}
