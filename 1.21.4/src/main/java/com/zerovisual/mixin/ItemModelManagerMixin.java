package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.ShieldColor;
import com.zerovisual.util.ShieldOverlayHolder;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The holder of an item is only known here, before rendering: remember the ShieldColor overlay in the render state. */
@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {
	@Inject(method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V", at = @At("TAIL"))
	private void zerovisual$shieldColor(ItemRenderState renderState, ItemStack stack, ModelTransformationMode mode, boolean leftHand,
										World world, LivingEntity entity, int seed, CallbackInfo ci) {
		ZeroVisualClient client = ZeroVisualClient.get();
		int overlay = client == null ? -1 : client.modules().get(ShieldColor.class).overlayFor(stack, entity);
		((ShieldOverlayHolder) renderState).zerovisual$setShieldOverlay(overlay);
	}
}
