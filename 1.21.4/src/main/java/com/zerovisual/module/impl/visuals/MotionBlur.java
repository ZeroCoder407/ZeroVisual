package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Blends the previous frames into the current one after the world is drawn (before the HUD),
 * which leaves a short trail when the camera moves. The blend is scaled by frame time,
 * so the trail looks the same at 60 and at 300 FPS.
 */
public class MotionBlur extends Module {
	public final NumberSetting strength = add(new NumberSetting("Strength", 0.5, 0.1, 0.9, 0.05));

	private SimpleFramebuffer history;
	private boolean fresh = true;
	private long lastFrame;

	public MotionBlur() {
		super("MotionBlur", "Motion blur when the camera moves", Category.VISUALS);
	}

	/** Called from GameRendererMixin right after the world is rendered into the main framebuffer. */
	public void apply() {
		Framebuffer main = mc.getFramebuffer();
		int w = main.textureWidth, h = main.textureHeight;
		if (history == null) {
			history = new SimpleFramebuffer(w, h, false);
			fresh = true;
		} else if (history.textureWidth != w || history.textureHeight != h) {
			history.resize(w, h);
			fresh = true;
		}

		long now = System.nanoTime();
		float frameMs = lastFrame == 0 ? 16.6f : Math.min(100f, (now - lastFrame) / 1_000_000f);
		lastFrame = now;

		if (!fresh) {
			// share of the previous image that is kept, normalized to a 60 FPS frame
			float keep = (float) Math.pow(strength.get(), frameMs / 16.6f);
			main.beginWrite(false);
			drawHistory(keep);
		}
		fresh = false;

		GlStateManager._glBindFramebuffer(GlConst.GL_READ_FRAMEBUFFER, main.fbo);
		GlStateManager._glBindFramebuffer(GlConst.GL_DRAW_FRAMEBUFFER, history.fbo);
		GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GlConst.GL_COLOR_BUFFER_BIT, GlConst.GL_NEAREST);
		main.beginWrite(true);
	}

	/**
	 * Draws the history over the main framebuffer with the vanilla blit shader. Blending uses a constant alpha,
	 * because the alpha channel of the world image is not meaningful (often 0).
	 */
	private void drawHistory(float alpha) {
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		GL14.glBlendColor(0, 0, 0, alpha);
		RenderSystem.blendFuncSeparate(GL14.GL_CONSTANT_ALPHA, GL14.GL_ONE_MINUS_CONSTANT_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager._colorMask(true, true, true, false);
		ShaderProgram shader = RenderSystem.setShader(ShaderProgramKeys.BLIT_SCREEN);
		if (shader != null) {
			shader.addSamplerTexture("InSampler", history.getColorAttachment());
			BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.BLIT_SCREEN);
			buffer.vertex(0, 0, 0);
			buffer.vertex(1, 0, 0);
			buffer.vertex(1, 1, 0);
			buffer.vertex(0, 1, 0);
			BufferRenderer.drawWithGlobalProgram(buffer.end());
		}
		GlStateManager._colorMask(true, true, true, true);
		GL14.glBlendColor(0, 0, 0, 0);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
	}

	@Override
	protected void onEnable() {
		fresh = true;
	}

	@Override
	protected void onDisable() {
		if (history != null) {
			history.delete();
			history = null;
		}
	}
}
