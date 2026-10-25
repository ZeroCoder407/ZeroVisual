package com.zerovisual.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.ZeroVisualClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import com.zerovisual.module.impl.utilities.ClickGui;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Anti-aliased rounded shapes (SDF shader) and float-positioned text.
 * Shapes are drawn immediately, so the batched DrawContext is flushed first to keep draw order.
 */
public final class Render2D {
	private static final ShaderProgramKey ROUNDED_RECT = new ShaderProgramKey(
		Identifier.of(ZeroVisualClient.MOD_ID, "core/rounded_rect"), VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);

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
		draw(ctx, x, y, w, h, radius, 0, 0, color, color, color, color);
	}

	public static void gradientH(DrawContext ctx, float x, float y, float w, float h, float radius, int left, int right) {
		draw(ctx, x, y, w, h, radius, 0, 0, left, right, right, left);
	}

	public static void gradientV(DrawContext ctx, float x, float y, float w, float h, float radius, int top, int bottom) {
		draw(ctx, x, y, w, h, radius, 0, 0, top, top, bottom, bottom);
	}

	public static void outline(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
		draw(ctx, x, y, w, h, radius, thickness, 0, color, color, color, color);
	}

	/** Soft shadow / glow around a rounded rectangle. */
	public static void shadow(DrawContext ctx, float x, float y, float w, float h, float radius, float softness, int color) {
		draw(ctx, x, y, w, h, radius, 0, softness, color, color, color, color);
	}

	public static void circle(DrawContext ctx, float cx, float cy, float r, int color) {
		rect(ctx, cx - r, cy - r, r * 2, r * 2, r, color);
	}

	private static void draw(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, float softness,
							 int topLeft, int topRight, int bottomRight, int bottomLeft) {
		if (w <= 0 || h <= 0 || alpha <= 0) return;
		ctx.draw();
		if (!shape(ctx.getMatrices().peek().getPositionMatrix(), x, y, w, h, radius, thickness, softness,
			topLeft, topRight, bottomRight, bottomLeft)) {
			ctx.fill((int) x, (int) y, (int) (x + w), (int) (y + h), applyAlpha(topLeft));
		}
	}

	/**
	 * Draws a rounded shape with an arbitrary matrix (also usable in world space).
	 * The caller is responsible for depth / cull state.
	 *
	 * @return false if the shader is unavailable
	 */
	public static boolean shape(Matrix4f m, float x, float y, float w, float h, float radius, float thickness, float softness,
								int topLeft, int topRight, int bottomRight, int bottomLeft) {
		ShaderProgram shader = RenderSystem.setShader(ROUNDED_RECT);
		if (shader == null) return false;

		float hw = w / 2f;
		float hh = h / 2f;
		setUniform(shader, "Size", hw, hh);
		setUniform(shader, "Radius", Math.min(radius, Math.min(hw, hh)));
		setUniform(shader, "Thickness", thickness);
		setUniform(shader, "Softness", softness);

		float pad = softness + 1f;
		float x0 = x - pad, y0 = y - pad, x1 = x + w + pad, y1 = y + h + pad;
		float u = hw + pad, v = hh + pad;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		buffer.vertex(m, x0, y0, 0).texture(-u, -v).color(applyAlpha(topLeft));
		buffer.vertex(m, x0, y1, 0).texture(-u, v).color(applyAlpha(bottomLeft));
		buffer.vertex(m, x1, y1, 0).texture(u, v).color(applyAlpha(bottomRight));
		buffer.vertex(m, x1, y0, 0).texture(u, -v).color(applyAlpha(topRight));
		BufferRenderer.drawWithGlobalProgram(buffer.end());
		RenderSystem.disableBlend();
		return true;
	}

	private static void setUniform(ShaderProgram shader, String name, float... values) {
		GlUniform uniform = shader.getUniform(name);
		if (uniform == null) return;
		if (values.length == 2) uniform.set(values[0], values[1]);
		else uniform.set(values[0]);
	}

	private static int applyAlpha(int argb) {
		if (alpha >= 1f) return argb;
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	// ---- Text ----

	public static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	/** Font chosen in ClickGUI, or null for the vanilla Minecraft font. */
	@Nullable
	private static Identifier currentFont() {
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client == null) return null;
		return client.modules().get(ClickGui.class).getFontId();
	}

	/** Wraps the string in the selected font. */
	public static Text styled(String text) {
		Identifier font = currentFont();
		MutableText literal = Text.literal(text);
		return font == null ? literal : literal.setStyle(Style.EMPTY.withFont(font));
	}

	public static int textWidth(String text) {
		return font().getWidth(styled(text));
	}

	public static void text(DrawContext ctx, String text, float x, float y, int color) {
		text(ctx, text, x, y, color, false);
	}

	public static void text(DrawContext ctx, String text, float x, float y, int color, boolean shadow) {
		int c = applyAlpha(color);
		// TextRenderer treats alpha < 4 as fully opaque, so skip nearly invisible text.
		if (((c >>> 24) & 0xFF) < 5) return;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 0);
		ctx.drawText(font(), styled(text), 0, 0, c, shadow);
		ctx.getMatrices().pop();
	}

	public static void centeredText(DrawContext ctx, String text, float centerX, float y, int color) {
		text(ctx, text, centerX - textWidth(text) / 2f, y, color);
	}

	public static void scaledText(DrawContext ctx, String text, float x, float y, float scale, int color) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 0);
		ctx.getMatrices().scale(scale, scale, 1);
		text(ctx, text, 0, 0, color);
		ctx.getMatrices().pop();
	}
}
