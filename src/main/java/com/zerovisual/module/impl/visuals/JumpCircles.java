package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effect on the ground where you jump:
 * Circle - expanding ring; Star - the logo star grows and spins, slowing down, and fades with a small shockwave;
 * Shatter - the star appears, then breaks into its five rays which fly apart.
 */
public class JumpCircles extends Module {
	/** Logo gradient, left to right. */
	private static final int STAR_LEFT = 0xFF388CFF;
	private static final int STAR_RIGHT = 0xFFB050F5;
	/** Inner radius of the star relative to the outer one (same as the logo). */
	private static final float INNER = 0.40f;
	private static final float APPEAR_SECONDS = 0.25f;
	private static final float SHATTER_SECONDS = 0.6f;
	/** Base opacity of the star, slightly see-through so it blends with the ground. */
	private static final float STAR_ALPHA = 0.9f;

	public final ModeSetting mode = add(new ModeSetting("Mode", "Circle", "Circle", "Star", "Shatter"));
	public final NumberSetting radius = add(new NumberSetting("Radius", 1.2, 0.5, 3, 0.1));
	public final NumberSetting duration = add(new NumberSetting("Duration", 1.2, 0.4, 3, 0.1));
	/** Shatter: how long the star stays whole before it breaks. */
	public final NumberSetting breakAfter = add(new NumberSetting("Break after", 0.8, 0.2, 3, 0.1));

	private final List<Circle> circles = new ArrayList<>();
	private boolean wasOnGround = true;

	public JumpCircles() {
		super("JumpCircles", "Circles or the logo star on the ground when you jump", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		circles.clear();
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		boolean onGround = mc.player.isOnGround();
		if (wasOnGround && !onGround && mc.player.getVelocity().y > 0.1) spawn(mc.player.getPos());
		wasOnGround = onGround;
	}

	public void spawn(Vec3d pos) {
		// a random start angle so stars of consecutive jumps do not look identical
		circles.add(new Circle(pos, System.currentTimeMillis(), (float) (Math.random() * 72)));
	}

	/** Total lifetime of one effect in milliseconds for the current mode. */
	private float lifetime() {
		return mode.is("Shatter") ? (APPEAR_SECONDS + breakAfter.getFloat() + SHATTER_SECONDS) * 1000f : duration.getFloat() * 1000f;
	}

	public void render(WorldRenderContext context) {
		if (circles.isEmpty()) return;
		Vec3d cam = context.camera().getPos();
		long now = System.currentTimeMillis();
		float life = lifetime();

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();

		Iterator<Circle> it = circles.iterator();
		while (it.hasNext()) {
			Circle circle = it.next();
			float elapsed = now - circle.start;
			if (elapsed >= life) {
				it.remove();
				continue;
			}
			Matrix4f m = new Matrix4f()
				.translate((float) (circle.pos.x - cam.x), (float) (circle.pos.y - cam.y) + 0.02f, (float) (circle.pos.z - cam.z))
				.rotateX((float) Math.toRadians(90));
			switch (mode.get()) {
				case "Star" -> renderStar(m, circle, elapsed / life);
				case "Shatter" -> renderShatter(m, circle, elapsed / 1000f);
				default -> renderCircle(m, elapsed / life);
			}
		}

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
	}

	private void renderCircle(Matrix4f m, float t) {
		int accent = Theme.accent();
		int light = Theme.accentLight();
		float r = 0.15f + radius.getFloat() * easeOut(t);
		float fade = 1f - t;
		// glow + ring with a gradient across it
		Render2D.shape(m, -r, -r, r * 2, r * 2, r, 0, r * 0.25f,
			ColorUtil.withAlpha(accent, 0.25f * fade), ColorUtil.withAlpha(light, 0.25f * fade),
			ColorUtil.withAlpha(light, 0.25f * fade), ColorUtil.withAlpha(accent, 0.25f * fade));
		Render2D.shape(m, -r, -r, r * 2, r * 2, r, 0.06f, 0,
			ColorUtil.withAlpha(accent, fade), ColorUtil.withAlpha(light, fade),
			ColorUtil.withAlpha(light, fade), ColorUtil.withAlpha(accent, fade));
	}

	/** Grows from nothing while spinning fast, the spin slows down, then it fades out. */
	private void renderStar(Matrix4f m, Circle circle, float t) {
		float size = radius.getFloat() * easeOut(Math.min(1f, t / 0.45f));
		// two and a half turns, most of them at the start
		float angle = circle.angle + 900f * easeOut(t);
		float alpha = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
		Matrix4f star = new Matrix4f(m).rotateZ((float) Math.toRadians(angle));
		drawStar(star, size, alpha);
		// a small shockwave while the star fades out
		if (t > 0.68f) {
			float w = (t - 0.68f) / 0.32f;
			float r = radius.getFloat() * (0.35f + 0.95f * easeOut(w));
			float fade = (1f - w) * 0.85f;
			int a = ColorUtil.withAlpha(STAR_LEFT, fade), b = ColorUtil.withAlpha(STAR_RIGHT, fade);
			Render2D.shape(m, -r, -r, r * 2, r * 2, r, 0.05f * (1f - w) + 0.015f, r * 0.12f, a, b, b, a);
		}
	}

	/** Appears, stays whole for "Break after" seconds, then its five rays fly apart and fade. */
	private void renderShatter(Matrix4f m, Circle circle, float seconds) {
		float r = radius.getFloat();
		float appear = Math.min(1f, seconds / APPEAR_SECONDS);
		float breakAt = APPEAR_SECONDS + breakAfter.getFloat();
		// a short spin while it appears, then it stands still until it breaks
		Matrix4f star = new Matrix4f(m).rotateZ((float) Math.toRadians(circle.angle + 180f * easeOut(appear)));
		if (seconds < breakAt) {
			drawStar(star, r * easeOut(appear), 1f);
			return;
		}
		float p = Math.min(1f, (seconds - breakAt) / SHATTER_SECONDS);
		float distance = r * 0.9f * easeOut(p);
		float alpha = 1f - p * p;
		for (int i = 0; i < 5; i++) {
			double dir = -Math.PI / 2 + i * 2 * Math.PI / 5;
			Matrix4f piece = new Matrix4f(star)
				.translate((float) Math.cos(dir) * distance, (float) Math.sin(dir) * distance, 0)
				// each ray tumbles a little, alternating direction
				.rotateZ((float) Math.toRadians((i % 2 == 0 ? 1 : -1) * 120f * p))
				.scale(1f - 0.35f * p);
			drawRay(piece, r, i, alpha);
		}
	}

	private static void drawStar(Matrix4f m, float r, float alpha) {
		if (r <= 0.001f || alpha <= 0.01f) return;
		BufferBuilder b = begin();
		// soft glow: a larger, faint copy of the star
		for (int i = 0; i < 5; i++) addRay(b, m, r * 1.18f, i, alpha * 0.22f);
		for (int i = 0; i < 5; i++) addRay(b, m, r, i, alpha);
		end(b);
	}

	private static void drawRay(Matrix4f m, float r, int index, float alpha) {
		if (alpha <= 0.01f) return;
		BufferBuilder b = begin();
		addRay(b, m, r * 1.18f, index, alpha * 0.22f);
		addRay(b, m, r, index, alpha);
		end(b);
	}

	/** One ray of the star: center, inner corner, tip, inner corner. The color follows the logo gradient. */
	private static void addRay(BufferBuilder b, Matrix4f m, float r, int index, float alpha) {
		double tip = -Math.PI / 2 + index * 2 * Math.PI / 5;
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
		alpha *= STAR_ALPHA;
		float t = Math.max(0f, Math.min(1f, (x + r) / (2 * r)));
		b.vertex(m, x, y, 0).color(ColorUtil.withAlpha(ColorUtil.lerp(STAR_LEFT, STAR_RIGHT, t), alpha));
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

	private static float easeOut(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	private record Circle(Vec3d pos, long start, float angle) {
	}
}
