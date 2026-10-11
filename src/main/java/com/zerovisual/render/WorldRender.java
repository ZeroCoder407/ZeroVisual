package com.zerovisual.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Helpers for effects drawn in the world from WorldRenderEvents.
 * Positions are camera-relative: the camera rotation is already on the model-view stack.
 */
public final class WorldRender {
	private WorldRender() {
	}

	/** Matrix at a world position, facing the camera. */
	public static Matrix4f billboard(Vec3d pos, Vec3d cam, Quaternionf cameraRotation) {
		return new Matrix4f()
			.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
			.rotate(cameraRotation);
	}

	/** Matrix lying flat (XZ plane) at a world position. */
	public static Matrix4f flat(Vec3d pos, Vec3d cam) {
		return new Matrix4f()
			.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
			.rotateX((float) Math.toRadians(90));
	}

	/** Soft glowing dot: a blurred halo plus a solid core. */
	public static void glowDot(Matrix4f m, float radius, int color) {
		int halo = ColorUtil.fade(color, 0.45f);
		Render2D.shape(m, -radius * 3, -radius * 3, radius * 6, radius * 6, radius * 3, 0, radius * 2.5f, halo, halo, halo, halo);
		Render2D.shape(m, -radius, -radius, radius * 2, radius * 2, radius, 0, 0, color, color, color, color);
	}

	/** Ring with a gradient and a soft glow. */
	public static void ring(Matrix4f m, float radius, float thickness, int colorA, int colorB, float glow) {
		if (glow > 0) {
			int ga = ColorUtil.fade(colorA, 0.35f), gb = ColorUtil.fade(colorB, 0.35f);
			Render2D.shape(m, -radius, -radius, radius * 2, radius * 2, radius, 0, glow, ga, gb, gb, ga);
		}
		Render2D.shape(m, -radius, -radius, radius * 2, radius * 2, radius, thickness, 0, colorA, colorB, colorB, colorA);
	}

	public static void begin() {
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
	}

	public static void end() {
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
	}
}
