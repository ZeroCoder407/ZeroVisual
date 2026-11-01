package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ZvPipelines;
import com.zerovisual.setting.NumberSetting;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Optional;

/**
 * Blends the previous frames into the current one after the world is drawn (before the HUD),
 * which leaves a short trail when the camera moves. The blend is scaled by frame time,
 * so the trail looks the same at 60 and at 300 FPS.
 */
public class MotionBlur extends Module {
	public final NumberSetting strength = add(new NumberSetting("Strength", 0.5, 0.1, 0.9, 0.05));

	private TextureTarget history;
	private boolean fresh = true;
	private long lastFrame;

	public MotionBlur() {
		super("MotionBlur", "Motion blur when the camera moves", Category.VISUALS);
	}

	/** Called from GameRendererMixin right after the world is rendered into the main render target. */
	public void apply() {
		RenderTarget main = mc.getMainRenderTarget();
		if (main.getColorTexture() == null || main.getColorTextureView() == null) return;
		int w = main.width, h = main.height;
		if (history == null) {
			history = new TextureTarget("ZeroVisual motion blur", w, h, false);
			fresh = true;
		} else if (history.width != w || history.height != h) {
			history.resize(w, h);
			fresh = true;
		}

		long now = System.nanoTime();
		float frameMs = lastFrame == 0 ? 16.6f : Math.min(100f, (now - lastFrame) / 1_000_000f);
		lastFrame = now;

		if (!fresh) {
			// share of the previous image that is kept, normalized to a 60 FPS frame
			float keep = (float) Math.pow(strength.get(), frameMs / 16.6f);
			// alpha is not written: the alpha channel of the world image is not meaningful
			// uploads are not allowed inside a pass
			var transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(), new Vector4f(1, 1, 1, keep), new org.joml.Vector3f(), new Matrix4f());
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "ZeroVisual motion blur",
				main.getColorTextureView(), java.util.OptionalInt.empty())) {
				pass.setPipeline(ZvPipelines.MOTION_BLUR);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.bindTexture("InSampler", history.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.draw(0, 3);
			}
		}
		fresh = false;
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorTexture(), history.getColorTexture(), 0, 0, 0, 0, 0, w, h);
	}

	@Override
	protected void onEnable() {
		fresh = true;
	}

	@Override
	protected void onDisable() {
		if (history != null) {
			history.destroyBuffers();
			history = null;
		}
	}
}
