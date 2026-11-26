package com.zerovisual.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * What the newer versions get from the game's DrawContext, on 1.8.9: the GUI is drawn right away with the
 * fixed-function matrix stack, so most of this forwards to GlStateManager and Gui.
 */
public final class DrawContext {
	private final Minecraft mc = Minecraft.getMinecraft();
	private final ScaledResolution resolution;
	private final Matrices matrices = new Matrices();

	public DrawContext() {
		this(new ScaledResolution(Minecraft.getMinecraft()));
	}

	public DrawContext(ScaledResolution resolution) {
		this.resolution = resolution;
	}

	public ScaledResolution resolution() {
		return resolution;
	}

	public int getScaledWindowWidth() {
		return resolution.getScaledWidth();
	}

	public int getScaledWindowHeight() {
		return resolution.getScaledHeight();
	}

	public Matrices getMatrices() {
		return matrices;
	}

	/** Nothing is batched on 1.8.9. */
	public void draw() {
	}

	public void fill(int x1, int y1, int x2, int y2, int color) {
		Gui.drawRect(x1, y1, x2, y2, color);
		GlStateManager.color(1, 1, 1, 1);
	}

	public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
		Render2D.gradientV(this, x1, y1, x2 - x1, y2 - y1, 0, top, bottom);
	}

	public void drawItem(ItemStack stack, int x, int y) {
		if (stack == null) return;
		GlStateManager.pushMatrix();
		GlStateManager.enableRescaleNormal();
		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.enableDepth();
		mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableDepth();
		GlStateManager.disableRescaleNormal();
		GlStateManager.enableBlend();
		GlStateManager.popMatrix();
	}

	/** The stack count and the durability bar. */
	public void drawStackOverlay(ItemStack stack, int x, int y) {
		if (stack == null) return;
		mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, stack, x, y, null);
		GlStateManager.disableLighting();
		GlStateManager.enableBlend();
		GlStateManager.color(1, 1, 1, 1);
	}

	public void drawTexture(ResourceLocation texture, int x, int y, float u, float v, int w, int h, int textureW, int textureH) {
		mc.getTextureManager().bindTexture(texture);
		GlStateManager.enableBlend();
		GlStateManager.color(1, 1, 1, 1);
		Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, w, h, textureW, textureH);
	}

	/** Clips drawing to a rectangle in GUI coordinates, ignoring the matrix (as the vanilla scissor of the newer versions does not). */
	public void enableScissor(int x1, int y1, int x2, int y2) {
		int scale = resolution.getScaleFactor();
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(x1 * scale, mc.displayHeight - y2 * scale, Math.max(0, x2 - x1) * scale, Math.max(0, y2 - y1) * scale);
	}

	public void disableScissor() {
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}

	/** The fixed-function model-view matrix, with the method names of the newer versions' MatrixStack. */
	public static final class Matrices {
		public void push() {
			GlStateManager.pushMatrix();
		}

		public void pop() {
			GlStateManager.popMatrix();
		}

		public void translate(float x, float y, float z) {
			GlStateManager.translate(x, y, z);
		}

		public void scale(float x, float y, float z) {
			GlStateManager.scale(x, y, z);
		}

		/** Turns around the Z axis by {@code degrees}. */
		public void rotateZ(float degrees) {
			GlStateManager.rotate(degrees, 0, 0, 1);
		}
	}
}
