package com.zerovisual.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * The five-pointed logo star drawn as geometry (sharp at any size), with the logo's
 * left-to-right blue-to-purple gradient. Each ray is one quad: center, inner corner, tip, inner corner.
 * World versions take a camera-relative matrix, GUI versions the extractor's 2D pose.
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

	/** Direction of ray {@code index} in radians. */
	public static double rayAngle(int index) {
		return -Math.PI / 2 + index * 2 * Math.PI / 5;
	}

	// ---- world ----

	/** Whole star centered at the origin of {@code m}, tip pointing to -Y, with a faint star-shaped glow. */
	public static void draw(Matrix4fc m, float r, float alpha) {
		if (r <= 0.001f || alpha <= 0.01f) return;
		Matrix4f pose = new Matrix4f(m);
		float a = Render2D.getAlpha() * alpha;
		WorldRender.geometry(ZvPipelines.WORLD_COLOR_TYPE, buffer -> {
			for (int i = 0; i < 5; i++) addRay(buffer, pose, null, r * GLOW_SCALE, i, a * GLOW_ALPHA);
			for (int i = 0; i < 5; i++) addRay(buffer, pose, null, r, i, a);
		});
	}

	/** A single ray (0..4) of the star, for shattering effects. */
	public static void drawRay(Matrix4fc m, float r, int index, float alpha) {
		if (alpha <= 0.01f) return;
		Matrix4f pose = new Matrix4f(m);
		float a = Render2D.getAlpha() * alpha;
		WorldRender.geometry(ZvPipelines.WORLD_COLOR_TYPE, buffer -> {
			addRay(buffer, pose, null, r * GLOW_SCALE, index, a * GLOW_ALPHA);
			addRay(buffer, pose, null, r, index, a);
		});
	}

	/** One-colored star (a hit particle), additive so overlapping stars glow. */
	public static void drawSolid(Matrix4fc m, float r, int color) {
		if (r <= 0.001f || ((color >>> 24) & 0xFF) < 3) return;
		Matrix4f pose = new Matrix4f(m);
		int c = Render2D.applyAlpha(color);
		WorldRender.geometry(ZvPipelines.WORLD_COLOR_ADDITIVE_TYPE, buffer -> {
			for (int i = 0; i < 5; i++) {
				double tip = rayAngle(i), step = Math.PI / 5;
				buffer.addVertex(pose, 0, 0, 0).setColor(c);
				buffer.addVertex(pose, (float) Math.cos(tip - step) * r * INNER, (float) Math.sin(tip - step) * r * INNER, 0).setColor(c);
				buffer.addVertex(pose, (float) Math.cos(tip) * r, (float) Math.sin(tip) * r, 0).setColor(c);
				buffer.addVertex(pose, (float) Math.cos(tip + step) * r * INNER, (float) Math.sin(tip + step) * r * INNER, 0).setColor(c);
			}
		});
	}

	// ---- GUI ----

	/** Whole star at the origin of the extractor's current pose (translate/rotate it first). */
	public static void draw(GuiGraphics ctx, float r, float alpha) {
		if (r <= 0.001f || alpha <= 0.01f) return;
		ctx.guiRenderState.submitGuiElement(new StarState(new Matrix3x2f(ctx.pose()), r, -1, Render2D.getAlpha() * alpha, ctx.scissorStack.peek()));
	}

	public static void drawRay(GuiGraphics ctx, float r, int index, float alpha) {
		if (alpha <= 0.01f) return;
		ctx.guiRenderState.submitGuiElement(new StarState(new Matrix3x2f(ctx.pose()), r, index, Render2D.getAlpha() * alpha, ctx.scissorStack.peek()));
	}

	/** The star (ray = -1) or a single ray as a GUI element. */
	private record StarState(Matrix3x2fc pose, float r, int ray, float alpha, @Nullable ScreenRectangle scissorArea,
							 @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
		StarState(Matrix3x2fc pose, float r, int ray, float alpha, @Nullable ScreenRectangle scissorArea) {
			this(pose, r, ray, alpha, scissorArea, bounds(pose, r, scissorArea));
		}

		private static @Nullable ScreenRectangle bounds(Matrix3x2fc pose, float r, @Nullable ScreenRectangle scissorArea) {
			int s = (int) Math.ceil(r * GLOW_SCALE) + 1;
			ScreenRectangle rect = new ScreenRectangle(-s, -s, s * 2, s * 2).transformMaxBounds(pose);
			return scissorArea != null ? scissorArea.intersection(rect) : rect;
		}

		@Override
		public void buildVertices(VertexConsumer buffer) {
			int from = ray < 0 ? 0 : ray, to = ray < 0 ? 4 : ray;
			for (int i = from; i <= to; i++) addRay(buffer, null, pose, r * GLOW_SCALE, i, alpha * GLOW_ALPHA);
			for (int i = from; i <= to; i++) addRay(buffer, null, pose, r, i, alpha);
		}

		@Override
		public RenderPipeline pipeline() {
			return RenderPipelines.GUI;
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}

	// ---- geometry ----

	private static void addRay(VertexConsumer b, @Nullable Matrix4fc m, @Nullable Matrix3x2fc pose2d, float r, int index, float alpha) {
		double tip = rayAngle(index);
		double step = Math.PI / 5;
		float tx = (float) Math.cos(tip) * r, ty = (float) Math.sin(tip) * r;
		float lx = (float) Math.cos(tip - step) * r * INNER, ly = (float) Math.sin(tip - step) * r * INNER;
		float rx = (float) Math.cos(tip + step) * r * INNER, ry = (float) Math.sin(tip + step) * r * INNER;
		// the GUI pipeline culls back faces: same winding as vanilla GUI quads (the world pipelines draw both sides)
		vertex(b, m, pose2d, 0, 0, r, alpha);
		vertex(b, m, pose2d, rx, ry, r, alpha);
		vertex(b, m, pose2d, tx, ty, r, alpha);
		vertex(b, m, pose2d, lx, ly, r, alpha);
	}

	private static void vertex(VertexConsumer b, @Nullable Matrix4fc m, @Nullable Matrix3x2fc pose2d, float x, float y, float r, float alpha) {
		float t = Math.max(0f, Math.min(1f, (x + r) / (2 * r)));
		int color = ColorUtil.withAlpha(ColorUtil.lerp(LEFT, RIGHT, t), alpha);
		(pose2d != null ? b.addVertexWith2DPose(pose2d, x, y) : b.addVertex(m, x, y, 0)).setColor(color);
	}
}
