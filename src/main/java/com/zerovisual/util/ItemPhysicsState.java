package com.zerovisual.util;

/** Extra data added to ItemEntityRenderState by ItemEntityRenderStateMixin. */
public interface ItemPhysicsState {
	boolean zerovisual$isOnGround();

	void zerovisual$setOnGround(boolean onGround);
}
