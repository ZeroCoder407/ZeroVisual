package com.zerovisual.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
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

	public static void rect(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius, int color) {
		draw(ctx, x, y, w, h, radius, 0, 0, color, color, color, color);
	}

	public static void gradientH(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius, int left, int right) {
		draw(ctx, x, y, w, h, radius, 0, 0, left, right, right, left);
	}

	public static void gradientV(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius, int top, int bottom) {
		draw(ctx, x, y, w, h, radius, 0, 0, top, top, bottom, bottom);
	}

	public static void outline(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
		draw(ctx, x, y, w, h, radius, thickness, 0, color, color, color, color);
	}

	/** Soft shadow / glow around a rounded rectangle. */
	public static void shadow(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius, float softness, int color) {
		draw(ctx, x, y, w, h, radius, 0, softness, color, color, color, color);
	}

	public static void circle(GuiGraphicsExtractor ctx, float cx, float cy, float r, int color) {
		rect(ctx, cx - r, cy - r, r * 2, r * 2, r, color);
	}

	/** Any rounded shape with four corner colors (top left, top right, bottom right, bottom left). */
	public static void shape(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius, float thickness, float softness,
							 int topLeft, int topRight, int bottomRight, int bottomLeft) {
		draw(ctx, x, y, w, h, radius, thickness, softness, topLeft, topRight, bottomRight, bottomLeft);
	}

	private static void draw(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius, float thickness, float softness,
							 int topLeft, int topRight, int bottomRight, int bottomLeft) {
		if (w <= 0 || h <= 0 || alpha <= 0) return;
		ctx.guiRenderState.addGuiElement(new ShapeState(new Matrix3x2f(ctx.pose()), x, y, w, h, radius, thickness, softness,
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
		vertex(buffer, pose2d, pose3d, x0, y0, topLeft).setUv(-u, -v).setUv3(r, thickness * k).setUv1(shw, shh).setLineWidth(softness * k);
		vertex(buffer, pose2d, pose3d, x0, y1, bottomLeft).setUv(-u, v).setUv3(r, thickness * k).setUv1(shw, shh).setLineWidth(softness * k);
		vertex(buffer, pose2d, pose3d, x1, y1, bottomRight).setUv(u, v).setUv3(r, thickness * k).setUv1(shw, shh).setLineWidth(softness * k);
		vertex(buffer, pose2d, pose3d, x1, y0, topRight).setUv(u, -v).setUv3(r, thickness * k).setUv1(shw, shh).setLineWidth(softness * k);
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
		public com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline() {
			return ZvPipelines.GUI_SHAPE;
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

	public static void text(GuiGraphicsExtractor ctx, String text, float x, float y, int color) {
		text(ctx, text, x, y, color, false);
	}

	public static void text(GuiGraphicsExtractor ctx, String text, float x, float y, int color, boolean shadow) {
		int c = applyAlpha(color);
		// the font treats an alpha below 4 as fully opaque, so skip nearly invisible text
		if (((c >>> 24) & 0xFF) < 5) return;
		ctx.pose().pushMatrix();
		ctx.pose().translate(x, y);
		ctx.text(font(), styled(text), 0, 0, c, shadow);
		ctx.pose().popMatrix();
	}

	public static void centeredText(GuiGraphicsExtractor ctx, String text, float centerX, float y, int color) {
		text(ctx, text, centerX - textWidth(text) / 2f, y, color);
	}

	public static void scaledText(GuiGraphicsExtractor ctx, String text, float x, float y, float scale, int color) {
		ctx.pose().pushMatrix();
		ctx.pose().translate(x, y);
		ctx.pose().scale(scale, scale);
		text(ctx, text, 0, 0, color);
		ctx.pose().popMatrix();
	}
}
