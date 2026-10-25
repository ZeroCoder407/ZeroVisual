package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.cosmetics.KillEffect;
import com.zerovisual.module.impl.performance.EntityCulling;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	/** KillEffect (black hole) draws its own copy of the dying entity, so the real one is hidden meanwhile. */
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void zerovisual$hideDying(E entity, Frustum frustum, double x, double y, double z, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		if (client.modules().get(KillEffect.class).isHidden(entity)) {
			cir.setReturnValue(false);
			return;
		}
		// EntityCulling skips entities beyond its distance
		EntityCulling culling = client.modules().get(EntityCulling.class);
		if (culling.isEnabled() && culling.hides(entity, x, y, z)) cir.setReturnValue(false);
	}
}
