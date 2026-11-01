package com.zerovisual.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/**
 * What world effects need from a frame: the camera and the partial tick. Created in
 * WorldRenderEvents.BEFORE_ENTITIES; effects submit their geometry through {@link WorldRender}.
 */
public record WorldContext(Camera camera, DeltaTracker tickCounter, WorldRenderContext raw) {
	public static WorldContext of(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		return new WorldContext(mc.gameRenderer.getMainCamera(), mc.getDeltaTracker(), context);
	}

	/**
	 * View matrix for camera-relative positions. The game applies the camera rotation itself in 26.x,
	 * so this is the identity (kept for effects that used to multiply it in).
	 */
	/** The camera of this frame as the game passes it to entity renderers. */
	public net.minecraft.client.renderer.state.CameraRenderState cameraState() {
		return raw.worldState().cameraRenderState;
	}

	public Matrix4f positionMatrix() {
		return new Matrix4f();
	}
}
