package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.performance.EntityCulling;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** EntityCulling: far entities are not drawn; KillEffect hides the entities it draws itself. */
@Mixin(RenderManager.class)
public class RenderManagerMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void zerovisual$cull(Entity entity, ICamera camera, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		EntityCulling culling = client.modules().get(EntityCulling.class);
		if (culling.isEnabled() && culling.hides(entity, camX, camY, camZ)) cir.setReturnValue(false);
		// KillEffect draws the dying entity itself while it is pulled into the black hole
		if (client.modules().get(com.zerovisual.module.impl.cosmetics.KillEffect.class).isHidden(entity)) cir.setReturnValue(false);
	}
}
