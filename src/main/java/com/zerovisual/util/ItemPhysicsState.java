package com.zerovisual.util;

/** Extra data added to ItemEntityRenderState by ItemEntityRenderStateMixin. */
public interface ItemPhysicsState {
	boolean zerovisual$isOnGround();

	void zerovisual$setOnGround(boolean onGround);

	/** Horizontal velocity, blocks per tick. */
	float zerovisual$velocityX();

	float zerovisual$velocityZ();

	void zerovisual$setVelocity(float x, float z);
}
