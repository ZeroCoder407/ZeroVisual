package com.zerovisual.render;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/**
 * What world effects need from a frame: the camera and the partial tick. Created in
 * LevelRenderEvents.COLLECT_SUBMITS; effects submit their geometry through {@link WorldRender}.
 */
public record WorldContext(Camera camera, DeltaTracker tickCounter, LevelRenderContext raw) {
	public static WorldContext of(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		return new WorldContext(mc.gameRenderer.mainCamera(), mc.getDeltaTracker(), context);
	}

	/**
	 * View matrix for camera-relative positions. The game applies the camera rotation itself in 26.x,
	 * so this is the identity (kept for effects that used to multiply it in).
	 */
	/** The camera of this frame as the game passes it to entity renderers. */
	public net.minecraft.client.renderer.state.level.CameraRenderState cameraState() {
		return raw.levelState().cameraRenderState;
	}

	public Matrix4f positionMatrix() {
		return new Matrix4f();
	}
}
