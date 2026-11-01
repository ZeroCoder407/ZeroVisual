package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.HitboxColor;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** HitboxColor: the F3+B box of the entity under the crosshair gets the module color. */
@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {
	@ModifyVariable(method = "showHitboxes", at = @At("STORE"), ordinal = 0)
	private int zerovisual$hitboxColor(int color, Entity entity, float partialTicks, boolean isServerEntity) {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null || isServerEntity) return color;
		HitboxColor hitbox = client.modules().get(HitboxColor.class);
		return hitbox.recolors(entity) ? hitbox.boxColor() : color;
	}
}
