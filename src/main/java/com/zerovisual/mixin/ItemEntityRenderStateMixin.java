package com.zerovisual.mixin;

import com.zerovisual.util.ItemPhysicsState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements ItemPhysicsState {
	@Unique
	private boolean zerovisual$onGround;

	@Override
	public boolean zerovisual$isOnGround() {
		return zerovisual$onGround;
	}

	@Override
	public void zerovisual$setOnGround(boolean onGround) {
		zerovisual$onGround = onGround;
	}
}
