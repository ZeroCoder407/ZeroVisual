package com.zerovisual.module.impl.cosmetics;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Glowing stars (or circles) bursting out of the entity you hit. */
public class HitParticles extends Module {
	private static final Identifier STAR = Identifier.of(ZeroVisualClient.MOD_ID, "textures/gui/star_white.png");
	private static final int MAX_PARTICLES = 400;

	public final ModeSetting shape = add(new ModeSetting("Shape", "Stars", "Stars", "Circles"));
	public final NumberSetting count = add(new NumberSetting("Count", 12, 3, 40, 1));
	public final NumberSetting size = add(new NumberSetting("Size", 0.18, 0.05, 0.5, 0.01));
	public final NumberSetting lifetime = add(new NumberSetting("Lifetime", 1.2, 0.4, 3, 0.1));
	public final BooleanSetting gravity = add(new BooleanSetting("Gravity", true));

	private final List<Particle> particles = new ArrayList<>();
	private final Random random = new Random();
	private long lastFrame;

	public HitParticles() {
		super("HitParticles", "Glowing stars when you hit a target", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		particles.clear();
	}

	public void spawn(Entity target) {
		Vec3d center = target.getBoundingBox().getCenter();
		int n = count.getInt();
		for (int i = 0; i < n && particles.size() < MAX_PARTICLES; i++) {
			double yaw = random.nextDouble() * Math.PI * 2;
			double pitch = (random.nextDouble() - 0.3) * Math.PI * 0.8;
			double speed = 1.5 + random.nextDouble() * 3;
			Vec3d velocity = new Vec3d(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch) + 0.4, Math.sin(yaw) * Math.cos(pitch)).multiply(speed);
			float hue = random.nextFloat();
			particles.add(new Particle(center, velocity, 0.6f + random.nextFloat() * 0.8f, random.nextFloat() * 360,
				(random.nextFloat() - 0.5f) * 540, hue));
		}
	}

	public void render(WorldRenderContext context) {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		if (particles.isEmpty()) return;

		float life = lifetime.getFloat();
		float g = gravity.get() ? 9f : 0f;
		Iterator<Particle> it = particles.iterator();
		while (it.hasNext()) {
			Particle p = it.next();
			p.age += dt;
			if (p.age >= life) {
				it.remove();
				continue;
			}
			p.velocity = p.velocity.multiply(Math.pow(0.35, dt)).add(0, -g * dt, 0);
			p.pos = p.pos.add(p.velocity.multiply(dt));
			p.rotation += p.spin * dt;
		}
		if (particles.isEmpty()) return;

		Vec3d cam = context.camera().getPos();
		Quaternionf rotation = context.camera().getRotation();
		int accent = Theme.accent();
		int light = Theme.accentLight();

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();

		if (shape.is("Stars")) {
			RenderSystem.enableBlend();
			// additive blending makes overlapping stars glow
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
			RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
			RenderSystem.setShaderTexture(0, STAR);
			BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
			for (Particle p : particles) {
				float t = p.age / life;
				float s = size.getFloat() * p.scale * (1f - t * 0.5f);
				int color = ColorUtil.withAlpha(ColorUtil.lerp(accent, light, p.hue), 1f - t * t);
				Matrix4f m = billboard(p, cam, rotation);
				buffer.vertex(m, -s, -s, 0).texture(0, 1).color(color);
				buffer.vertex(m, s, -s, 0).texture(1, 1).color(color);
				buffer.vertex(m, s, s, 0).texture(1, 0).color(color);
				buffer.vertex(m, -s, s, 0).texture(0, 0).color(color);
			}
			BufferRenderer.drawWithGlobalProgram(buffer.end());
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableBlend();
		} else {
			for (Particle p : particles) {
				float t = p.age / life;
				float s = size.getFloat() * p.scale * 0.6f * (1f - t * 0.5f);
				int color = ColorUtil.withAlpha(ColorUtil.lerp(accent, light, p.hue), 1f - t * t);
				Matrix4f m = billboard(p, cam, rotation);
				Render2D.shape(m, -s * 2, -s * 2, s * 4, s * 4, s * 2, 0, s, ColorUtil.fade(color, 0.5f), ColorUtil.fade(color, 0.5f), ColorUtil.fade(color, 0.5f), ColorUtil.fade(color, 0.5f));
				Render2D.shape(m, -s, -s, s * 2, s * 2, s, 0, 0, color, color, color, color);
			}
		}

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
	}

	private static Matrix4f billboard(Particle p, Vec3d cam, Quaternionf rotation) {
		return new Matrix4f()
			.translate((float) (p.pos.x - cam.x), (float) (p.pos.y - cam.y), (float) (p.pos.z - cam.z))
			.rotate(rotation)
			.rotateZ((float) Math.toRadians(p.rotation));
	}

	private static class Particle {
		Vec3d pos;
		Vec3d velocity;
		final float scale;
		float rotation;
		final float spin;
		final float hue;
		float age;

		Particle(Vec3d pos, Vec3d velocity, float scale, float rotation, float spin, float hue) {
			this.pos = pos;
			this.velocity = velocity;
			this.scale = scale;
			this.rotation = rotation;
			this.spin = spin;
			this.hue = hue;
		}
	}
}
