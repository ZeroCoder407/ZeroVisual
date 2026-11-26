package com.zerovisual.render;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.ClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Anti-aliased rounded shapes (SDF shader) and float-positioned text, with the API of the newer versions.
 * Everything is drawn immediately with the current GL matrix.
 */
public final class Render2D {
	private static final Shader ROUNDED_RECT = new Shader("rounded_rect");

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

	public static void rect(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
		draw(x, y, w, h, radius, 0, 0, color, color, color, color);
	}

	public static void gradientH(DrawContext ctx, float x, float y, float w, float h, float radius, int left, int right) {
		draw(x, y, w, h, radius, 0, 0, left, right, right, left);
	}

	public static void gradientV(DrawContext ctx, float x, float y, float w, float h, float radius, int top, int bottom) {
		draw(x, y, w, h, radius, 0, 0, top, top, bottom, bottom);
	}

	public static void outline(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
		draw(x, y, w, h, radius, thickness, 0, color, color, color, color);
	}

	/** Soft shadow / glow around a rounded rectangle. */
	public static void shadow(DrawContext ctx, float x, float y, float w, float h, float radius, float softness, int color) {
		draw(x, y, w, h, radius, 0, softness, color, color, color, color);
	}

	public static void circle(DrawContext ctx, float cx, float cy, float r, int color) {
		rect(ctx, cx - r, cy - r, r * 2, r * 2, r, color);
	}

	/** A part of a ring: angles in radians, clockwise from the right; the color goes from the inner to the outer edge. */
	public static void sector(DrawContext ctx, float cx, float cy, float inner, float outer, float from, float to, int innerColor, int outerColor) {
		if (alpha <= 0) return;
		begin();
		GlStateManager.disableTexture2D();
		final WorldRenderer buffer = Tessellator.getInstance().getWorldRenderer();
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		sectorMesh(new Corner() {
			@Override
			public void put(float x, float y, int color) {
				buffer.pos(x, y, 0).color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24).endVertex();
			}
		}, cx, cy, inner, outer, from, to, applyAlpha(innerColor), applyAlpha(outerColor));
		Tessellator.getInstance().draw();
		end();
	}

	private static void draw(float x, float y, float w, float h, float radius, float thickness, float softness,
							 int topLeft, int topRight, int bottomRight, int bottomLeft) {
		if (w <= 0 || h <= 0 || alpha <= 0) return;
		begin();
		if (!shape(x, y, w, h, radius, thickness, softness, topLeft, topRight, bottomRight, bottomLeft)) {
			GlStateManager.disableTexture2D();
			quad(x, y, x + w, y + h, applyAlpha(topLeft), applyAlpha(topRight), applyAlpha(bottomRight), applyAlpha(bottomLeft));
		}
		end();
	}

	/**
	 * Draws a rounded shape with the current GL matrix (also usable in world space).
	 * The caller is responsible for depth / cull state.
	 *
	 * @return false if the shader is unavailable
	 */
	public static boolean shape(float x, float y, float w, float h, float radius, float thickness, float softness,
								int topLeft, int topRight, int bottomRight, int bottomLeft) {
		if (!ROUNDED_RECT.bind()) return false;
		float hw = w / 2f;
		float hh = h / 2f;
		ROUNDED_RECT.set("Size", hw, hh);
		ROUNDED_RECT.set("Radius", Math.min(radius, Math.min(hw, hh)));
		ROUNDED_RECT.set("Thickness", thickness);
		ROUNDED_RECT.set("Softness", softness);

		float pad = softness + 1f;
		float x0 = x - pad, y0 = y - pad, x1 = x + w + pad, y1 = y + h + pad;
		// the shape is placed around its center: the texture coordinates are relative to it
		float cx = x + hw, cy = y + hh;
		WorldRenderer buffer = Tessellator.getInstance().getWorldRenderer();
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
		vertex(buffer, x0, y0, x0 - cx, y0 - cy, applyAlpha(topLeft));
		vertex(buffer, x0, y1, x0 - cx, y1 - cy, applyAlpha(bottomLeft));
		vertex(buffer, x1, y1, x1 - cx, y1 - cy, applyAlpha(bottomRight));
		vertex(buffer, x1, y0, x1 - cx, y0 - cy, applyAlpha(topRight));
		Tessellator.getInstance().draw();
		ROUNDED_RECT.unbind();
		return true;
	}

	/** A rounded shape in the plane of a matrix (world effects); see {@link WorldRender#shape}. */
	public static boolean shape(org.joml.Matrix4f m, float x, float y, float w, float h, float radius, float thickness, float softness,
								int topLeft, int topRight, int bottomRight, int bottomLeft) {
		WorldRender.shape(m, x, y, w, h, radius, thickness, softness, topLeft, topRight, bottomRight, bottomLeft);
		return true;
	}

	private static void vertex(WorldRenderer buffer, float x, float y, float u, float v, int color) {
		buffer.pos(x, y, 0).tex(u, v).color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24).endVertex();
	}

	private static void quad(float x0, float y0, float x1, float y1, int topLeft, int topRight, int bottomRight, int bottomLeft) {
		WorldRenderer buffer = Tessellator.getInstance().getWorldRenderer();
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		buffer.pos(x0, y0, 0).color(topLeft >> 16 & 255, topLeft >> 8 & 255, topLeft & 255, topLeft >>> 24).endVertex();
		buffer.pos(x0, y1, 0).color(bottomLeft >> 16 & 255, bottomLeft >> 8 & 255, bottomLeft & 255, bottomLeft >>> 24).endVertex();
		buffer.pos(x1, y1, 0).color(bottomRight >> 16 & 255, bottomRight >> 8 & 255, bottomRight & 255, bottomRight >>> 24).endVertex();
		buffer.pos(x1, y0, 0).color(topRight >> 16 & 255, topRight >> 8 & 255, topRight & 255, topRight >>> 24).endVertex();
		Tessellator.getInstance().draw();
	}

	/** Blending on, textures and culling off: what every shape here needs; {@link #end} puts the GUI defaults back. */
	private static void begin() {
		GlStateManager.enableBlend();
		GlStateManager.disableAlpha();
		GlStateManager.disableCull();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		GlStateManager.color(1, 1, 1, 1);
	}

	private static void end() {
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.enableTexture2D();
		GlStateManager.enableCull();
		GlStateManager.enableAlpha();
		GlStateManager.color(1, 1, 1, 1);
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

	/** Quads of a part of a ring. The geometry has no anti-aliasing of its own, so every edge fades out over {@link #SECTOR_EDGE}. */
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

	// ---- Text ----

	public static FontRenderer font() {
		return Minecraft.getMinecraft().fontRendererObj;
	}

	/** Font chosen in ClickGUI, or null for the vanilla Minecraft font. */
	private static TtfFont currentFont() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return null;
		String name = client.modules().get(ClickGui.class).getFontName();
		return name == null ? null : TtfFont.get(name);
	}

	public static int textWidth(String text) {
		return Math.round(textWidthF(text));
	}

	public static float textWidthF(String text) {
		TtfFont font = currentFont();
		return font == null ? font().getStringWidth(text) : font.width(text);
	}

	public static void text(DrawContext ctx, String text, float x, float y, int color) {
		text(ctx, text, x, y, color, false);
	}

	public static void text(DrawContext ctx, String text, float x, float y, int color, boolean shadow) {
		int c = applyAlpha(color);
		// the vanilla font treats alpha < 4 as fully opaque, so skip nearly invisible text
		if (((c >>> 24) & 0xFF) < 5) return;
		TtfFont font = currentFont();
		if (font != null) {
			font.draw(text, x, y, c, shadow);
		} else {
			GlStateManager.enableBlend();
			font().drawString(text, x, y, c, shadow);
		}
		GlStateManager.color(1, 1, 1, 1);
	}

	public static void centeredText(DrawContext ctx, String text, float centerX, float y, int color) {
		text(ctx, text, centerX - textWidthF(text) / 2f, y, color);
	}

	public static void scaledText(DrawContext ctx, String text, float x, float y, float scale, int color) {
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, 0);
		GlStateManager.scale(scale, scale, 1);
		text(ctx, text, 0, 0, color);
		GlStateManager.popMatrix();
	}
}
