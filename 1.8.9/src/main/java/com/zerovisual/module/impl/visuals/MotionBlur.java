package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * Blends the previous frames into the current one after the world is drawn (before the HUD),
 * which leaves a short trail when the camera moves. The blend is scaled by frame time,
 * so the trail looks the same at 60 and at 300 FPS.
 */
public class MotionBlur extends Module {
	public final NumberSetting strength = add(new NumberSetting("Strength", 0.5, 0.1, 0.9, 0.05));

	private Framebuffer history;
	private boolean fresh = true;
	private long lastFrame;

	public MotionBlur() {
		super("MotionBlur", "Motion blur when the camera moves", Category.VISUALS);
	}

	/** Called before the HUD is drawn, with the world finished in the main framebuffer and a GUI projection. */
	public void apply(int scaledWidth, int scaledHeight) {
		if (!OpenGlHelper.isFramebufferEnabled()) return;
		Framebuffer main = mc.getFramebuffer();
		int w = main.framebufferWidth, h = main.framebufferHeight;
		if (history == null) {
			history = new Framebuffer(w, h, false);
			fresh = true;
		} else if (history.framebufferWidth != w || history.framebufferHeight != h) {
			history.createBindFramebuffer(w, h);
			fresh = true;
		}
		long now = System.nanoTime();
		float frameMs = lastFrame == 0 ? 16.6f : Math.min(100f, (now - lastFrame) / 1_000_000f);
		lastFrame = now;
		if (!fresh) {
			// share of the previous image that is kept, normalized to a 60 FPS frame
			float keep = (float) Math.pow(strength.get(), frameMs / 16.6f);
			// in framebuffer pixels: the GUI size is rounded up at odd sizes, and a fraction of a pixel of offset
			// would add up frame after frame into a smear over part of the screen
			GlStateManager.matrixMode(GL11.GL_PROJECTION);
			GlStateManager.pushMatrix();
			GlStateManager.loadIdentity();
			GlStateManager.ortho(0, w, h, 0, -1, 1);
			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
			GlStateManager.pushMatrix();
			GlStateManager.loadIdentity();
			drawHistory(keep, w, h);
			GlStateManager.popMatrix();
			GlStateManager.matrixMode(GL11.GL_PROJECTION);
			GlStateManager.popMatrix();
			GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		}
		fresh = false;
		OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.framebufferObject);
		OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, history.framebufferObject);
		GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
		main.bindFramebuffer(true);
	}

	/** The history over the screen with a constant alpha: the alpha channel of the world image means nothing. */
	private void drawHistory(float alpha, int w, int h) {
		GlStateManager.disableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.enableBlend();
		GlStateManager.disableAlpha();
		GL14.glBlendColor(0, 0, 0, alpha);
		GlStateManager.tryBlendFuncSeparate(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE_MINUS_CONSTANT_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.colorMask(true, true, true, false);
		GlStateManager.enableTexture2D();
		GlStateManager.color(1, 1, 1, 1);
		history.bindFramebufferTexture();
		WorldRenderer b = Tessellator.getInstance().getWorldRenderer();
		b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
		b.pos(0, h, 0).tex(0, 0).endVertex();
		b.pos(w, h, 0).tex(1, 0).endVertex();
		b.pos(w, 0, 0).tex(1, 1).endVertex();
		b.pos(0, 0, 0).tex(0, 1).endVertex();
		Tessellator.getInstance().draw();
		history.unbindFramebufferTexture();
		GlStateManager.colorMask(true, true, true, true);
		GL14.glBlendColor(0, 0, 0, 0);
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.enableAlpha();
		GlStateManager.depthMask(true);
		GlStateManager.enableDepth();
	}

	@Override
	protected void onEnable() {
		fresh = true;
	}

	@Override
	protected void onDisable() {
		if (history != null) {
			history.deleteFramebuffer();
			history = null;
		}
	}
}
