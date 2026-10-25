package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.render.SpaceTextures;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Replaces the overworld sky (SkyRenderingMixin):
 * Galaxy - nebulae, the milky way and twinkling stars;
 * Realistic - an atmosphere that follows the sun, with sunsets, a glowing sun, the moon and stars at night;
 * Black hole - deep space with a huge black hole and its accretion disk, as seen from a planet.
 */
public class CustomSky extends Module {
	private static final float DISTANCE = 40;
	private static final int STARS = 1800;
	/** Fixed direction of the black hole in the sky. */
	private static final Vector3f HOLE_DIR = new Vector3f(0.55f, 0.36f, -0.75f).normalize();

	public final ModeSetting mode = add(new ModeSetting("Mode", "Galaxy", "Galaxy", "Realistic", "Black hole"));
	public final NumberSetting stars = add(new NumberSetting("Stars", 1, 0, 2, 0.1));

	private final SpaceTextures galaxy = new SpaceTextures(SpaceTextures.GALAXY);
	private final SpaceTextures deep = new SpaceTextures(SpaceTextures.DEEP);
	private final float[][] starData = new float[STARS][];
	/** Fog color for the current frame, so the horizon blends into the sky. */
	private float fogR, fogG, fogB;

	public CustomSky() {
		super("CustomSky", "Galaxy, realistic sky or a black hole instead of the vanilla sky", Category.VISUALS);
		Random random = new Random(20241);
		for (int i = 0; i < STARS; i++) {
			// uniform directions on the sphere
			float z = random.nextFloat() * 2 - 1, a = random.nextFloat() * MathHelper.TAU;
			float r = (float) Math.sqrt(1 - z * z);
			float size = 0.035f + (float) Math.pow(random.nextFloat(), 6) * 0.17f;
			float tint = random.nextFloat();
			starData[i] = new float[]{r * MathHelper.cos(a), z, r * MathHelper.sin(a), size, tint, random.nextFloat() * 10, 0.5f + random.nextFloat() * 2.5f};
		}
	}

	/** Whether the vanilla overworld sky is replaced right now. */
	public boolean isActive() {
		return isEnabled() && mc.world != null;
	}

	/** Clouds do not belong in space (WorldRendererMixin). */
	public boolean hidesClouds() {
		return isActive() && !mode.is("Realistic");
	}

	public float[] fogColor() {
		return new float[]{fogR, fogG, fogB};
	}

	/** Called instead of SkyRendering#renderSky; the model-view matrix already holds the camera rotation. */
	public void render() {
		float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
		float skyAngle = mc.world.getSkyAngle(tickDelta);
		float rain = mc.world.getRainGradient(tickDelta);
		float time = (System.currentTimeMillis() % 3_600_000L) / 1000f;

		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();

		// the night sky turns with the time of day, like vanilla stars
		Matrix4f celestial = new Matrix4f().rotateY((float) Math.toRadians(-90)).rotateX(skyAngle * MathHelper.TAU);
		switch (mode.get()) {
			case "Realistic" -> renderRealistic(celestial, skyAngle, rain, time);
			case "Black hole" -> {
				drawSpace(deep, celestial, 1f);
				drawStars(celestial, 1f, time);
				drawBlackHole(time);
				setFog(0.015f, 0.012f, 0.02f);
			}
			default -> {
				drawSpace(galaxy, celestial, 1f);
				drawStars(celestial, 1f, time);
				setFog(0.03f, 0.025f, 0.07f);
			}
		}

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.defaultBlendFunc();
	}

	private void setFog(float r, float g, float b) {
		fogR = r;
		fogG = g;
		fogB = b;
	}

	// ---- space ----

	private void drawSpace(SpaceTextures textures, Matrix4f m, float alpha) {
		Identifier[] ids = textures.get();
		if (ids == null || alpha <= 0.01f) return;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
		int color = ColorUtil.withAlpha(0xFFFFFFFF, alpha);
		for (int f = 0; f < 6; f++) {
			float[][] face = SpaceTextures.FACES[f];
			RenderSystem.setShaderTexture(0, ids[f]);
			BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
			cubeVertex(b, m, face, -1, -1, color);
			cubeVertex(b, m, face, -1, 1, color);
			cubeVertex(b, m, face, 1, 1, color);
			cubeVertex(b, m, face, 1, -1, color);
			BufferRenderer.drawWithGlobalProgram(b.end());
		}
		RenderSystem.disableBlend();
	}

	private static void cubeVertex(BufferBuilder b, Matrix4f m, float[][] face, float s, float t, int color) {
		float x = (face[0][0] + face[1][0] * s + face[2][0] * t) * DISTANCE;
		float y = (face[0][1] + face[1][1] * s + face[2][1] * t) * DISTANCE;
		float z = (face[0][2] + face[1][2] * s + face[2][2] * t) * DISTANCE;
		b.vertex(m, x, y, z).texture((s + 1) / 2, (t + 1) / 2).color(color);
	}

	private void drawStars(Matrix4f m, float alpha, float time) {
		float amount = stars.getFloat() * alpha;
		if (amount <= 0.01f) return;
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		Vector3f dir = new Vector3f(), u = new Vector3f(), v = new Vector3f();
		for (float[] star : starData) {
			dir.set(star[0], star[1], star[2]);
			// two axes perpendicular to the direction
			u.set(Math.abs(dir.y) < 0.9f ? 0 : 1, Math.abs(dir.y) < 0.9f ? 1 : 0, 0).cross(dir).normalize();
			v.set(dir).cross(u);
			float twinkle = 0.65f + 0.35f * MathHelper.sin(time * star[6] + star[5]);
			float a = Math.min(1f, twinkle * amount);
			int color = ColorUtil.withAlpha(star[4] < 0.15f ? 0xFFFFD9A8 : star[4] > 0.8f ? 0xFFAFC8FF : 0xFFFFFFFF, a);
			float size = star[3] * (stars.getFloat() > 1 ? 1 + (stars.getFloat() - 1) * 0.5f : 1);
			float cx = dir.x * DISTANCE * 0.95f, cy = dir.y * DISTANCE * 0.95f, cz = dir.z * DISTANCE * 0.95f;
			b.vertex(m, cx - u.x * size - v.x * size, cy - u.y * size - v.y * size, cz - u.z * size - v.z * size).color(color);
			b.vertex(m, cx - u.x * size + v.x * size, cy - u.y * size + v.y * size, cz - u.z * size + v.z * size).color(color);
			b.vertex(m, cx + u.x * size + v.x * size, cy + u.y * size + v.y * size, cz + u.z * size + v.z * size).color(color);
			b.vertex(m, cx + u.x * size - v.x * size, cy + u.y * size - v.y * size, cz + u.z * size - v.z * size).color(color);
		}
		BufferRenderer.drawWithGlobalProgram(b.end());
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	/** Matrix at a sky direction facing the camera (the camera rotation is already on the model-view stack). */
	private Matrix4f skyBillboard(Vector3f dir, float distance) {
		Quaternionf rotation = mc.gameRenderer.getCamera().getRotation();
		return new Matrix4f().translate(dir.x * distance, dir.y * distance, dir.z * distance).rotate(rotation);
	}

	// ---- black hole ----

	private void drawBlackHole(float time) {
		Matrix4f m = skyBillboard(HOLE_DIR, DISTANCE * 0.8f);
		float r = 5.5f;
		int hot = 0xFFFFF1D0, warm = 0xFFFFA24A, cool = 0xFFB4542A;

		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		// wide glow
		int glow = ColorUtil.withAlpha(warm, 0.22f);
		Render2D.shape(m, -r * 4, -r * 4, r * 8, r * 8, r * 4, 0, r * 3.2f, glow, glow, glow, glow);

		Matrix4f disk = new Matrix4f(m).rotateZ((float) Math.toRadians(-9));
		// back half of the accretion disk (behind the hole), then the light bent over the top and under the bottom
		annulus(disk, r * 1.35f, r * 3.4f, 0.24f, 0, MathHelper.PI, hot, warm, cool, time, 0.85f);
		annulus(disk, r * 1.03f, r * 1.5f, 1f, 0, MathHelper.PI, hot, warm, cool, time, 0.9f);
		annulus(disk, r * 1.02f, r * 1.22f, 1f, MathHelper.PI, MathHelper.TAU, hot, warm, cool, time, 0.45f);

		// the event horizon
		RenderSystem.defaultBlendFunc();
		Render2D.shape(m, -r, -r, r * 2, r * 2, r, 0, r * 0.04f, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000);

		// front half of the disk passes in front of it
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		annulus(disk, r * 1.35f, r * 3.4f, 0.24f, MathHelper.PI, MathHelper.TAU, hot, warm, cool, time, 1f);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	/**
	 * Part of a ring between two radii, squashed vertically by {@code squash} (an ellipse seen at an angle).
	 * Hot near the inner edge, fading outwards; the left side is brighter (the disk turns towards us there)
	 * and bright streaks move around it.
	 */
	private static void annulus(Matrix4f m, float inner, float outer, float squash, float from, float to,
								int hot, int warm, int cool, float time, float intensity) {
		int segments = 96, rings = 6;
		// additive: Render2D.shape in between resets the blend state
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		for (int ring = 0; ring < rings; ring++) {
			float k0 = (float) ring / rings, k1 = (float) (ring + 1) / rings;
			for (int i = 0; i < segments; i++) {
				float a0 = from + (to - from) * i / segments, a1 = from + (to - from) * (i + 1) / segments;
				vertex(b, m, inner, outer, squash, a0, k0, hot, warm, cool, time, intensity);
				vertex(b, m, inner, outer, squash, a0, k1, hot, warm, cool, time, intensity);
				vertex(b, m, inner, outer, squash, a1, k1, hot, warm, cool, time, intensity);
				vertex(b, m, inner, outer, squash, a1, k0, hot, warm, cool, time, intensity);
			}
		}
		BufferRenderer.drawWithGlobalProgram(b.end());
	}

	private static void vertex(BufferBuilder b, Matrix4f m, float inner, float outer, float squash, float angle, float k,
							   int hot, int warm, int cool, float time, float intensity) {
		float radius = inner + (outer - inner) * k;
		float x = MathHelper.cos(angle) * radius, y = MathHelper.sin(angle) * radius * squash;
		int color = k < 0.35f ? ColorUtil.lerp(hot, warm, k / 0.35f) : ColorUtil.lerp(warm, cool, (k - 0.35f) / 0.65f);
		// soft inner edge, fading outer edge
		float edge = Math.min(1f, k / 0.08f) * (1f - k * k);
		float doppler = 0.65f + 0.45f * -MathHelper.cos(angle);
		float streaks = 0.8f + 0.2f * MathHelper.sin(angle * 7 - time * 1.5f + k * 9);
		float a = Math.min(1f, edge * doppler * streaks * intensity);
		b.vertex(m, x, y, 0).color(ColorUtil.withAlpha(color, a));
	}

	// ---- realistic ----

	private void renderRealistic(Matrix4f celestial, float skyAngle, float rain, float time) {
		// same orbit as the vanilla sun: rotated around X by the sky angle, then -90 degrees around Y
		float angle = skyAngle * MathHelper.TAU;
		Vector3f sun = new Vector3f(-MathHelper.sin(angle), MathHelper.cos(angle), 0).normalize();
		float day = smoothstep(-0.22f, 0.25f, sun.y);
		float night = (1f - day) * (1f - rain);

		drawSpace(galaxy, celestial, night * 0.55f);
		drawStars(celestial, night, time);
		drawAtmosphere(sun, day, rain);
		drawSunAndMoon(sun, day, rain);

		Vec3d look = Vec3d.fromPolar(0, mc.gameRenderer.getCamera().getYaw());
		float[] horizon = skyColor(new Vector3f((float) look.x, 0.02f, (float) look.z).normalize(), sun, day, rain);
		setFog(horizon[0], horizon[1], horizon[2]);
	}

	private void drawAtmosphere(Vector3f sun, float day, float rain) {
		int stacks = 24, slices = 48;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		Matrix4f m = new Matrix4f();
		Vector3f d = new Vector3f();
		for (int i = 0; i < stacks; i++) {
			// from straight down to straight up
			float p0 = -MathHelper.HALF_PI + MathHelper.PI * i / stacks, p1 = -MathHelper.HALF_PI + MathHelper.PI * (i + 1) / stacks;
			for (int j = 0; j < slices; j++) {
				float y0 = MathHelper.TAU * j / slices, y1 = MathHelper.TAU * (j + 1) / slices;
				atmosphereVertex(b, m, d, p0, y0, sun, day, rain);
				atmosphereVertex(b, m, d, p1, y0, sun, day, rain);
				atmosphereVertex(b, m, d, p1, y1, sun, day, rain);
				atmosphereVertex(b, m, d, p0, y1, sun, day, rain);
			}
		}
		BufferRenderer.drawWithGlobalProgram(b.end());
		RenderSystem.disableBlend();
	}

	private void atmosphereVertex(BufferBuilder b, Matrix4f m, Vector3f d, float pitch, float yaw, Vector3f sun, float day, float rain) {
		d.set(MathHelper.cos(pitch) * MathHelper.cos(yaw), MathHelper.sin(pitch), MathHelper.cos(pitch) * MathHelper.sin(yaw));
		float[] c = skyColor(d, sun, day, rain);
		// at night the sky is see-through so the stars show; it stays opaque below the horizon
		float alpha = Math.max(day, Math.min(1f, 0.25f + rain * 0.7f));
		if (d.y < 0) alpha = Math.max(alpha, Math.min(1f, -d.y * 6));
		b.vertex(m, d.x * DISTANCE, d.y * DISTANCE, d.z * DISTANCE).color(ColorUtil.withAlpha(
			0xFF000000 | to255(c[0]) << 16 | to255(c[1]) << 8 | to255(c[2]), alpha));
	}

	/** Simple atmosphere model: blue zenith, pale horizon, warm twilight around the sun. */
	private static float[] skyColor(Vector3f d, Vector3f sun, float day, float rain) {
		float up = Math.max(0, d.y);
		float h = (float) Math.pow(up, 0.45);
		float r = lerp(lerp(0.05f, 0.012f, h), lerp(0.62f, 0.20f, h), day);
		float g = lerp(lerp(0.06f, 0.016f, h), lerp(0.78f, 0.44f, h), day);
		float b = lerp(lerp(0.12f, 0.045f, h), lerp(0.96f, 0.88f, h), day);

		float cos = Math.max(0, d.dot(sun));
		// sunsets and sunrises: strongest when the sun is near the horizon, low in the sky, towards the sun
		float twilight = (float) Math.exp(-sun.y * sun.y / 0.035f);
		float low = (float) Math.pow(1 - up, 3);
		float warm = twilight * (low * (0.35f + 0.65f * cos * cos) + (float) Math.pow(cos, 6) * 0.6f);
		r += 1.0f * warm;
		g += 0.42f * warm;
		b += 0.12f * warm;
		// glow around the sun
		float halo = (float) Math.pow(cos, 24) * 0.35f * Math.max(day, twilight);
		r += halo;
		g += halo * 0.92f;
		b += halo * 0.75f;

		if (d.y < 0) {
			float k = Math.min(1f, -d.y * 3);
			r = lerp(r, r * 0.45f, k);
			g = lerp(g, g * 0.45f, k);
			b = lerp(b, b * 0.5f, k);
		}
		float gray = (r + g + b) / 3 * 0.75f;
		return new float[]{lerp(r, gray, rain * 0.8f), lerp(g, gray, rain * 0.8f), lerp(b, gray, rain * 0.8f)};
	}

	private void drawSunAndMoon(Vector3f sun, float day, float rain) {
		float clear = 1f - rain;
		if (clear <= 0.01f) return;
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
		if (sun.y > -0.15f) {
			Matrix4f m = skyBillboard(sun, DISTANCE * 0.9f);
			float set = 1f - smoothstep(0.0f, 0.35f, sun.y);
			int core = ColorUtil.lerp(0xFFFFFBEA, 0xFFFFB25C, set);
			Render2D.shape(m, -9, -9, 18, 18, 9, 0, 7, ColorUtil.withAlpha(core, 0.35f * clear), ColorUtil.withAlpha(core, 0.35f * clear),
				ColorUtil.withAlpha(core, 0.35f * clear), ColorUtil.withAlpha(core, 0.35f * clear));
			int disc = ColorUtil.withAlpha(core, clear);
			Render2D.shape(m, -1.6f, -1.6f, 3.2f, 3.2f, 1.6f, 0, 0.25f, disc, disc, disc, disc);
		}
		Vector3f moon = new Vector3f(sun).negate();
		if (moon.y > -0.15f) {
			Matrix4f m = skyBillboard(moon, DISTANCE * 0.9f);
			int glow = ColorUtil.withAlpha(0xFFB9C8FF, 0.18f * clear);
			Render2D.shape(m, -5, -5, 10, 10, 5, 0, 4, glow, glow, glow, glow);
			RenderSystem.defaultBlendFunc();
			int disc = ColorUtil.withAlpha(0xFFE8ECF5, clear);
			Render2D.shape(m, -1.3f, -1.3f, 2.6f, 2.6f, 1.3f, 0, 0.1f, disc, disc, disc, disc);
			// a few darker "seas"
			int sea = ColorUtil.withAlpha(0xFFB8BECC, 0.8f * clear);
			Render2D.shape(m, -0.7f, -0.6f, 0.6f, 0.5f, 0.25f, 0, 0.08f, sea, sea, sea, sea);
			Render2D.shape(m, 0.15f, 0.1f, 0.5f, 0.45f, 0.22f, 0, 0.08f, sea, sea, sea, sea);
		}
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	private static float smoothstep(float a, float b, float x) {
		float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static int to255(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255)));
	}
}
