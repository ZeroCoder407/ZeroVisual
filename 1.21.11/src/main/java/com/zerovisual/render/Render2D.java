package com.zerovisual.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4fc;

/**
 * Anti-aliased rounded shapes (SDF shader, see ZvPipelines) and float-positioned text for the HUD and screens.
 * Shapes are added to the frame's GUI render state like vanilla elements, so they keep draw order with text and items.
 */
public final class Render2D {
	/** How far the edges of a sector fade out. */
	private static final float SECTOR_EDGE = 0.6f;

	/** Global opacity multiplier, used for fade animations. */
	private static float alpha = 1f;

	private Render2D() {
	}

	public static void setAlpha(float value) {
		alpha = Math.max(0f, Math.min(1f, value));
	}

	public static float getAlpha() {
		return alpha;
	}

	public static void rect(GuiGraphics ctx, float x, float y, float w, float h, float radius, int color) {
		draw(ctx, x, y, w, h, radius, 0, 0, color, color, color, color);
	}

	public static void gradientH(GuiGraphics ctx, float x, float y, float w, float h, float radius, int left, int right) {
		draw(ctx, x, y, w, h, radius, 0, 0, left, right, right, left);
	}

	public static void gradientV(GuiGraphics ctx, float x, float y, float w, float h, float radius, int top, int bottom) {
		draw(ctx, x, y, w, h, radius, 0, 0, top, top, bottom, bottom);
	}

	public static void outline(GuiGraphics ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
		draw(ctx, x, y, w, h, radius, thickness, 0, color, color, color, color);
	}

	/** Soft shadow / glow around a rounded rectangle. */
	public static void shadow(GuiGraphics ctx, float x, float y, float w, float h, float radius, float softness, int color) {
		draw(ctx, x, y, w, h, radius, 0, softness, color, color, color, color);
	}

	public static void circle(GuiGraphics ctx, float cx, float cy, float r, int color) {
		rect(ctx, cx - r, cy - r, r * 2, r * 2, r, color);
	}

	/** A part of a ring: angles in radians, clockwise from the right; the color goes from the inner to the outer edge. */
	public static void sector(GuiGraphics ctx, float cx, float cy, float inner, float outer, float from, float to, int innerColor, int outerColor) {
		if (alpha <= 0) return;
		ctx.guiRenderState.submitGuiElement(new SectorState(new Matrix3x2f(ctx.pose()), cx, cy, inner, outer, from, to,
			applyAlpha(innerColor), applyAlpha(outerColor), ctx.scissorStack.peek()));
	}

	/** Any rounded shape with four corner colors (top left, top right, bottom right, bottom left). */
	public static void shape(GuiGraphics ctx, float x, float y, float w, float h, float radius, float thickness, float softness,
							 int topLeft, int topRight, int bottomRight, int bottomLeft) {
		draw(ctx, x, y, w, h, radius, thickness, softness, topLeft, topRight, bottomRight, bottomLeft);
	}

	private static void draw(GuiGraphics ctx, float x, float y, float w, float h, float radius, float thickness, float softness,
							 int topLeft, int topRight, int bottomRight, int bottomLeft) {
		if (w <= 0 || h <= 0 || alpha <= 0) return;
		ctx.guiRenderState.submitGuiElement(new ShapeState(new Matrix3x2f(ctx.pose()), x, y, w, h, radius, thickness, softness,
			applyAlpha(topLeft), applyAlpha(topRight), applyAlpha(bottomRight), applyAlpha(bottomLeft), ctx.scissorStack.peek()));
	}

	/**
	 * Writes the four vertices of a rounded shape. Used by the GUI element and by world effects
	 * (with a 3D matrix). All lengths are scaled to "shape units" so the integer half size keeps its precision.
	 */
	static void shapeVertices(VertexConsumer buffer, @Nullable Matrix3x2fc pose2d, @Nullable Matrix4fc pose3d,
							  float x, float y, float w, float h, float radius, float thickness, float softness,
							  int topLeft, int topRight, int bottomRight, int bottomLeft) {
		float hw = w / 2f, hh = h / 2f;
		float k = 512f / Math.max(hw, hh);
		// room for anti-aliasing: one pixel in the GUI, a few percent of the size in the world
		float pad = softness + (pose2d != null ? 1f : Math.max(hw, hh) * 0.05f);
		float x0 = x - pad, y0 = y - pad, x1 = x + w + pad, y1 = y + h + pad;
		float u = (hw + pad) * k, v = (hh + pad) * k;
		float r = Math.min(radius, Math.min(hw, hh)) * k;
		int shw = Math.round(hw * k), shh = Math.round(hh * k);
		// short integers here: scaled up so thin outlines keep their width (see shape.vsh)
		int rr = Math.round(r * 4), tt = Math.round(thickness * k * 16);
		vertex(buffer, pose2d, pose3d, x0, y0, topLeft).setUv(-u, -v).setUv1(shw, shh).setUv2(rr, tt).setLineWidth(softness * k);
		vertex(buffer, pose2d, pose3d, x0, y1, bottomLeft).setUv(-u, v).setUv1(shw, shh).setUv2(rr, tt).setLineWidth(softness * k);
		vertex(buffer, pose2d, pose3d, x1, y1, bottomRight).setUv(u, v).setUv1(shw, shh).setUv2(rr, tt).setLineWidth(softness * k);
		vertex(buffer, pose2d, pose3d, x1, y0, topRight).setUv(u, -v).setUv1(shw, shh).setUv2(rr, tt).setLineWidth(softness * k);
	}

	private static VertexConsumer vertex(VertexConsumer buffer, @Nullable Matrix3x2fc pose2d, @Nullable Matrix4fc pose3d, float x, float y, int color) {
		VertexConsumer v = pose2d != null ? buffer.addVertexWith2DPose(pose2d, x, y) : buffer.addVertex(pose3d, x, y, 0);
		return v.setColor(color);
	}

	static int applyAlpha(int argb) {
		if (alpha >= 1f) return argb;
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	/** Where a corner of {@link #sectorMesh} goes. */
	private interface Corner {
		void put(float x, float y, int color);
	}

	/**
	 * Quads of a part of a ring. The geometry has no anti-aliasing of its own, so every edge fades out over
	 * {@link #SECTOR_EDGE}; the winding is the one of vanilla GUI quads (the GUI pipeline culls back faces).
	 */
	private static void sectorMesh(Corner out, float cx, float cy, float inner, float outer, float from, float to, int innerColor, int outerColor) {
		float[] radii = {inner - SECTOR_EDGE, inner, outer, outer + SECTOR_EDGE};
		int steps = Math.max(1, (int) Math.ceil((to - from) * outer / 4));
		for (int j = 0; j < 3; j++) {
			for (int i = -1; i <= steps; i++) {
				sectorCorner(out, cx, cy, radii, j, i, steps, from, to, innerColor, outerColor);
				sectorCorner(out, cx, cy, radii, j, i + 1, steps, from, to, innerColor, outerColor);
				sectorCorner(out, cx, cy, radii, j + 1, i + 1, steps, from, to, innerColor, outerColor);
				sectorCorner(out, cx, cy, radii, j + 1, i, steps, from, to, innerColor, outerColor);
			}
		}
	}

	private static void sectorCorner(Corner out, float cx, float cy, float[] radii, int j, int i, int steps, float from, float to, int innerColor, int outerColor) {
		float r = radii[j];
		float side = SECTOR_EDGE / Math.max(r, 1);
		float angle = i < 0 ? from - side : i > steps ? to + side : from + (to - from) * i / steps;
		int color = ColorUtil.lerp(innerColor, outerColor, Math.max(0, Math.min(1, (r - radii[1]) / (radii[2] - radii[1]))));
		boolean edge = j == 0 || j == 3 || i < 0 || i > steps;
		out.put(cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r, edge ? color & 0x00FFFFFF : color);
	}

	/** A rounded shape as a GUI element, batched with the other elements of its stratum. */
	private record ShapeState(Matrix3x2fc pose, float x, float y, float w, float h, float radius, float thickness, float softness,
							  int topLeft, int topRight, int bottomRight, int bottomLeft,
							  @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
		ShapeState(Matrix3x2fc pose, float x, float y, float w, float h, float radius, float thickness, float softness,
				   int topLeft, int topRight, int bottomRight, int bottomLeft, @Nullable ScreenRectangle scissorArea) {
			this(pose, x, y, w, h, radius, thickness, softness, topLeft, topRight, bottomRight, bottomLeft, scissorArea,
				bounds(pose, x, y, w, h, softness, scissorArea));
		}

		private static @Nullable ScreenRectangle bounds(Matrix3x2fc pose, float x, float y, float w, float h, float softness,
														@Nullable ScreenRectangle scissorArea) {
			int pad = (int) Math.ceil(softness) + 2;
			ScreenRectangle rect = new ScreenRectangle((int) Math.floor(x) - pad, (int) Math.floor(y) - pad,
				(int) Math.ceil(w) + pad * 2, (int) Math.ceil(h) + pad * 2).transformMaxBounds(pose);
			return scissorArea != null ? scissorArea.intersection(rect) : rect;
		}

		@Override
		public void buildVertices(VertexConsumer buffer) {
			shapeVertices(buffer, pose, null, x, y, w, h, radius, thickness, softness, topLeft, topRight, bottomRight, bottomLeft);
		}

		@Override
		public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
			return ZvPipelines.GUI_SHAPE;
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}

	/** A part of a ring as a GUI element. */
	private record SectorState(Matrix3x2fc pose, float cx, float cy, float inner, float outer, float from, float to, int innerColor, int outerColor,
							   @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
		SectorState(Matrix3x2fc pose, float cx, float cy, float inner, float outer, float from, float to, int innerColor, int outerColor, @Nullable ScreenRectangle scissorArea) {
			this(pose, cx, cy, inner, outer, from, to, innerColor, outerColor, scissorArea, bounds(pose, cx, cy, outer, scissorArea));
		}

		private static @Nullable ScreenRectangle bounds(Matrix3x2fc pose, float cx, float cy, float outer, @Nullable ScreenRectangle scissorArea) {
			int r = (int) Math.ceil(outer) + 2;
			ScreenRectangle rect = new ScreenRectangle((int) Math.floor(cx) - r, (int) Math.floor(cy) - r, r * 2, r * 2).transformMaxBounds(pose);
			return scissorArea != null ? scissorArea.intersection(rect) : rect;
		}

		@Override
		public void buildVertices(VertexConsumer buffer) {
			sectorMesh((x, y, color) -> buffer.addVertexWith2DPose(pose, x, y).setColor(color), cx, cy, inner, outer, from, to, innerColor, outerColor);
		}

		@Override
		public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
			return net.minecraft.client.renderer.RenderPipelines.GUI;
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}

	// ---- Text ----

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Supplies the font chosen in ClickGUI, or null for the vanilla Minecraft font. Set by the client on init. */
	private static java.util.function.Supplier<@Nullable Identifier> fontProvider = () -> null;

	public static void setFontProvider(java.util.function.Supplier<@Nullable Identifier> provider) {
		fontProvider = provider;
	}

	@Nullable
	private static Identifier currentFont() {
		return fontProvider.get();
	}

	/** Wraps the string in the selected font. */
	public static Component styled(String text) {
		Identifier font = currentFont();
		MutableComponent literal = Component.literal(text);
		return font == null ? literal : literal.setStyle(Style.EMPTY.withFont(new FontDescription.Resource(font)));
	}

	public static int textWidth(String text) {
		return font().width(styled(text));
	}

	public static void text(GuiGraphics ctx, String text, float x, float y, int color) {
		text(ctx, text, x, y, color, false);
	}

	public static void text(GuiGraphics ctx, String text, float x, float y, int color, boolean shadow) {
		int c = applyAlpha(color);
		// the font treats an alpha below 4 as fully opaque, so skip nearly invisible text
		if (((c >>> 24) & 0xFF) < 5) return;
		ctx.pose().pushMatrix();
		ctx.pose().translate(x, y);
		ctx.drawString(font(), styled(text), 0, 0, c, shadow);
		ctx.pose().popMatrix();
	}

	public static void centeredText(GuiGraphics ctx, String text, float centerX, float y, int color) {
		text(ctx, text, centerX - textWidth(text) / 2f, y, color);
	}

	public static void scaledText(GuiGraphics ctx, String text, float x, float y, float scale, int color) {
		ctx.pose().pushMatrix();
		ctx.pose().translate(x, y);
		ctx.pose().scale(scale, scale);
		text(ctx, text, 0, 0, color);
		ctx.pose().popMatrix();
	}
}
