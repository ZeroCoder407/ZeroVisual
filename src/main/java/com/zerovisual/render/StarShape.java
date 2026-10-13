package com.zerovisual.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * The five-pointed logo star drawn as geometry (sharp at any size), with the logo's
 * left-to-right blue-to-purple gradient. Works in world space and in the HUD.
 * Callers disable culling: the triangle winding flips between world and GUI space.
 */
public final class StarShape {
	public static final int LEFT = 0xFF388CFF;
	public static final int RIGHT = 0xFFB050F5;
	/** Inner radius relative to the outer one, same as the logo. */
	public static final float INNER = 0.40f;
	private static final float GLOW_SCALE = 1.18f;
	private static final float GLOW_ALPHA = 0.22f;

	private StarShape() {
	}

	/** Whole star centered at the origin of {@code m}, tip pointing to -Y, with a faint star-shaped glow. */
	public static void draw(Matrix4f m, float r, float alpha) {
		if (r <= 0.001f || alpha <= 0.01f) return;
		BufferBuilder b = begin();
		for (int i = 0; i < 5; i++) addRay(b, m, r * GLOW_SCALE, i, alpha * GLOW_ALPHA);
		for (int i = 0; i < 5; i++) addRay(b, m, r, i, alpha);
		end(b);
	}

	/** A single ray (0..4) of the star, for shattering effects. */
	public static void drawRay(Matrix4f m, float r, int index, float alpha) {
		if (alpha <= 0.01f) return;
		BufferBuilder b = begin();
		addRay(b, m, r * GLOW_SCALE, index, alpha * GLOW_ALPHA);
		addRay(b, m, r, index, alpha);
		end(b);
	}

	/** Direction of ray {@code index} in radians. */
	public static double rayAngle(int index) {
		return -Math.PI / 2 + index * 2 * Math.PI / 5;
	}

	/** One ray: center, inner corner, tip, inner corner. The color follows the logo gradient. */
	private static void addRay(BufferBuilder b, Matrix4f m, float r, int index, float alpha) {
		double tip = rayAngle(index);
		double step = Math.PI / 5;
		float tx = (float) Math.cos(tip) * r, ty = (float) Math.sin(tip) * r;
		float lx = (float) Math.cos(tip - step) * r * INNER, ly = (float) Math.sin(tip - step) * r * INNER;
		float rx = (float) Math.cos(tip + step) * r * INNER, ry = (float) Math.sin(tip + step) * r * INNER;
		vertex(b, m, 0, 0, r, alpha);
		vertex(b, m, lx, ly, r, alpha);
		vertex(b, m, tx, ty, r, alpha);
		vertex(b, m, 0, 0, r, alpha);
		vertex(b, m, tx, ty, r, alpha);
		vertex(b, m, rx, ry, r, alpha);
	}

	private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, float r, float alpha) {
		float t = Math.max(0f, Math.min(1f, (x + r) / (2 * r)));
		b.vertex(m, x, y, 0).color(ColorUtil.withAlpha(ColorUtil.lerp(LEFT, RIGHT, t), alpha));
	}

	private static BufferBuilder begin() {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		return Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
	}

	private static void end(BufferBuilder b) {
		BufferRenderer.drawWithGlobalProgram(b.end());
		RenderSystem.disableBlend();
	}
}
