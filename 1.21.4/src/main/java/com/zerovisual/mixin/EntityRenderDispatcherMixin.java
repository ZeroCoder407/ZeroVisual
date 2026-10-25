package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.cosmetics.KillEffect;
import com.zerovisual.module.impl.performance.EntityCulling;
import com.zerovisual.module.impl.visuals.HitboxColor;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	/**
	 * KillEffect (black hole) draws its own copy of the dying entity, so the real one is hidden meanwhile.
	 * EntityCulling skips entities beyond its distance.
	 */
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void zerovisual$hideDying(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		if (client.modules().get(KillEffect.class).isHidden(entity)) {
			cir.setReturnValue(false);
			return;
		}
		EntityCulling culling = client.modules().get(EntityCulling.class);
		if (culling.isEnabled() && culling.hides(entity, x, y, z)) cir.setReturnValue(false);
	}

	/** HitboxColor: the F3+B box of the entity under the crosshair gets the module color. */
	@ModifyArgs(method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/EntityRenderer;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderHitbox(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;FFFF)V", ordinal = 0))
	private void zerovisual$hitboxColor(Args args) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return;
		HitboxColor hitbox = client.modules().get(HitboxColor.class);
		if (!hitbox.recolors(args.get(2))) return;
		int c = hitbox.boxColor();
		args.set(4, ((c >> 16) & 0xFF) / 255f);
		args.set(5, ((c >> 8) & 0xFF) / 255f);
		args.set(6, (c & 0xFF) / 255f);
	}
}
