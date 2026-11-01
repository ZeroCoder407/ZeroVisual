package com.zerovisual.fx;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Leaves, snow, petals or fluff falling around a point, carried by the wind, lying on the ground for a while. */
public final class SeasonFx {
	public enum Kind {
		AUTUMN(1.1f, 0.9f, false, 0xD9541E, 0xE8962A, 0xF0C03C, 0xA83A1E, 0xC4761F),
		WINTER(0.9f, 0.25f, true, 0xFFFFFF, 0xEAF4FF),
		SPRING(0.7f, 0.65f, false, 0xFFB7C9, 0xFFC9D6, 0xFF9DB8, 0xFFE0E8),
		SUMMER(0.22f, 0.5f, true, 0xFFFFFF, 0xF4F1E6);

		final float fall, sway;
		final boolean round;
		final int[] palette;

		Kind(float fall, float sway, boolean round, int... palette) {
			this.fall = fall;
			this.sway = sway;
			this.round = round;
			this.palette = palette;
		}
	}

	/** The height things land on in a block column. */
	@FunctionalInterface
	public interface Ground {
		double at(int x, int z);
	}

	private static final float TWO_PI = (float) (Math.PI * 2);
	private static final float HALF_PI = (float) (Math.PI / 2);
	private static final float FADE_IN = 0.6f, FADE_OUT = 1.5f, SETTLE = 0.25f;

	private final List<Flake> flakes = new ArrayList<>();
	private final Random random = new Random();
	private final Matrix4f matrix = new Matrix4f();
	private Kind kind = Kind.AUTUMN;
	private float time, stay, radius;
	private double centerX, centerZ;

	public void clear() {
		flakes.clear();
	}

	public void update(Kind kind, int count, float radius, float wind, float stay, float dt, double x, double y, double z, Ground ground) {
		if (kind != this.kind) {
			flakes.clear();
			this.kind = kind;
		}
		this.radius = radius;
		this.stay = stay;
		centerX = x;
		centerZ = z;
		time += dt;

		float angle = 1.5f * (float) Math.sin(time * 0.07f) + 0.6f * (float) Math.sin(time * 0.19f + 1);
		float gust = wind * (0.65f + 0.35f * (float) (Math.sin(time * 0.31f) * Math.sin(time * 0.13f + 2)));
		float windX = (float) Math.cos(angle) * gust, windZ = (float) Math.sin(angle) * gust;
		float height = Math.max(radius * 0.7f, 6);

		while (flakes.size() > count) flakes.removeLast();
		while (flakes.size() < count) {
			Flake flake = new Flake();
			flakes.add(flake);
			respawn(flake, x, y, z, height, false, ground);
		}

		for (Flake f : flakes) {
			f.age += dt;
			if (f.landed >= 0) {
				f.landed += dt;
				if (f.landed > stay || Math.abs(f.x - x) > radius || Math.abs(f.z - z) > radius) respawn(f, x, y, z, height, true, ground);
				continue;
			}
			float s = time * f.swaySpeed + f.phase;
			f.x += (windX * f.drag + Math.cos(s) * kind.sway) * dt;
			f.z += (windZ * f.drag + Math.sin(s * 0.8f) * kind.sway) * dt;
			f.y -= kind.fall * f.weight * (1 + 0.35f * Math.sin(s)) * dt;
			f.spin += f.spinSpeed * dt;
			f.x = wrap(f.x, x);
			f.z = wrap(f.z, z);

			if (f.y < y - height || f.y > Math.max(y + height * 1.5, f.ground + 6)) {
				respawn(f, x, y, z, height, true, ground);
				continue;
			}
			int bx = (int) Math.floor(f.x), bz = (int) Math.floor(f.z);
			if (bx != f.blockX || bz != f.blockZ) {
				f.blockX = bx;
				f.blockZ = bz;
				f.ground = ground.at(bx, bz);
				if (f.ground > f.y + 1) { // ran into a wall
					respawn(f, x, y, z, height, true, ground);
					continue;
				}
			}
			if (f.y <= f.ground + f.lift) {
				if (stay <= 0) {
					respawn(f, x, y, z, height, true, ground);
				} else {
					f.y = f.ground + f.lift;
					f.landed = 0;
				}
			}
		}
	}

	private double wrap(double value, double center) {
		if (value > center + radius) return value - radius * 2;
		if (value < center - radius) return value + radius * 2;
		return value;
	}

	private void respawn(Flake f, double x, double y, double z, float height, boolean fromAbove, Ground ground) {
		f.x = x + (random.nextDouble() * 2 - 1) * radius;
		f.z = z + (random.nextDouble() * 2 - 1) * radius;
		f.blockX = (int) Math.floor(f.x);
		f.blockZ = (int) Math.floor(f.z);
		f.ground = ground.at(f.blockX, f.blockZ);
		f.y = fromAbove
			? Math.max(y + height * (0.4 + 0.6 * random.nextDouble()), f.ground + 1 + random.nextDouble() * 3)
			: Math.max(y - height + random.nextDouble() * height * 2, f.ground + 0.5);
		f.age = 0;
		f.landed = -1;
		f.color = kind.palette[random.nextInt(kind.palette.length)];
	}

	/** Draws everything. {@code shade} darkens the colors with the light around, 1 is full daylight. */
	public void draw(Quads q, double camX, double camY, double camZ, Quaternionf cameraRotation, float size, float shade) {
		for (Flake f : flakes) {
			float x = (float) (f.x - camX), y = (float) (f.y - camY), z = (float) (f.z - camZ);
			float alpha = alpha(f, (float) Math.sqrt(x * x + y * y + z * z));
			if (alpha <= 0.01f) continue;
			float s = size * f.scale;
			matrix.translation(x, y, z);
			if (kind.round) {
				if (f.landed >= 0) matrix.rotateX(HALF_PI);
				else matrix.rotate(cameraRotation);
				int color = argb(f.color, alpha, shade);
				if (kind == Kind.WINTER) flake(q, s * 0.85f, color);
				else fluff(q, s * 0.8f, color);
			} else {
				float settle = f.landed < 0 ? 0 : Math.min(f.landed / SETTLE, 1);
				float tumble = (float) Math.IEEEremainder(f.spin, TWO_PI);
				float tilt = tumble + ((tumble < 0 ? -HALF_PI : HALF_PI) - tumble) * settle;
				float rock = 0.5f * (float) Math.sin(f.phase + f.spin * 0.6f) * (1 - settle);
				matrix.rotateY(f.phase + f.spin * 0.15f).rotateX(tilt).rotateZ(rock);
				float light = shade * (0.78f + 0.22f * (float) Math.abs(Math.cos(tilt)));
				if (kind == Kind.AUTUMN) leaf(q, s, argb(f.color, alpha, light), argb(f.color, alpha, light * 0.8f));
				else petal(q, s * 0.8f, argb(f.color, alpha, light), argb(0xFFF4F6, alpha, light));
			}
		}
	}

	private float alpha(Flake f, float distance) {
		float alpha = Math.min(f.age / FADE_IN, 1);
		float edge = (float) Math.max(Math.abs(f.x - centerX), Math.abs(f.z - centerZ)) / radius;
		alpha *= clamp((1 - edge) / 0.25f);
		alpha *= clamp((distance - 0.3f) / 0.9f); // nothing big passes right in front of the eye
		if (f.landed >= 0) alpha *= clamp((stay - f.landed) / FADE_OUT);
		return alpha;
	}

	private void leaf(Quads q, float s, int color, int dark) {
		Matrix4f m = matrix;
		float wide = 0.62f * s, low = 0.4f * s, fold = 0.18f * s;
		q.vertex(m, 0, 1.3f * s, 0, color);
		q.vertex(m, -wide, 0.25f * s, fold, color);
		q.vertex(m, -low, -0.55f * s, fold * 0.5f, color);
		q.vertex(m, 0, -s, 0, color);
		q.vertex(m, 0, 1.3f * s, 0, dark);
		q.vertex(m, wide, 0.25f * s, fold, dark);
		q.vertex(m, low, -0.55f * s, fold * 0.5f, dark);
		q.vertex(m, 0, -s, 0, dark);
		q.line(m, 0, -s, 0, -1.4f * s, 0.08f * s, dark, dark);
	}

	private void petal(Quads q, float s, int color, int base) {
		Matrix4f m = matrix;
		float wide = 0.58f * s, fold = 0.14f * s;
		for (int side = -1; side <= 1; side += 2) {
			q.vertex(m, 0, 0.82f * s, fold * 0.5f, color);
			q.vertex(m, side * wide * 0.8f, s, fold, color);
			q.vertex(m, side * wide, 0.1f * s, fold, color);
			q.vertex(m, 0, -s, 0, base);
		}
	}

	private void flake(Quads q, float r, int color) {
		fan(q, r, color, color & 0x00FFFFFF);
	}

	private void fluff(Quads q, float r, int color) {
		fan(q, r * 0.55f, color, color & 0x00FFFFFF);
		for (int i = 0; i < 3; i++) {
			float a = i * TWO_PI / 6 + 0.3f;
			float dx = (float) Math.cos(a) * r, dy = (float) Math.sin(a) * r;
			q.line(matrix, -dx, -dy, dx, dy, r * 0.09f, color, color);
		}
	}

	private void fan(Quads q, float r, int center, int edge) {
		for (int i = 0; i < 6; i++) {
			float a0 = i * TWO_PI / 6, a1 = (i + 1) * TWO_PI / 6;
			q.triangle(matrix, 0, 0, 0, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r, 0, (float) Math.cos(a1) * r, (float) Math.sin(a1) * r, 0, center, edge, edge);
		}
	}

	private static int argb(int rgb, float alpha, float light) {
		int r = (int) (((rgb >> 16) & 255) * light), g = (int) (((rgb >> 8) & 255) * light), b = (int) ((rgb & 255) * light);
		return (int) (alpha * 255) << 24 | r << 16 | g << 8 | b;
	}

	private static float clamp(float value) {
		return Math.max(0, Math.min(value, 1));
	}

	private final class Flake {
		double x, y, z, ground;
		int blockX, blockZ;
		float age, spin;
		float landed = -1; // seconds on the ground, negative while in the air
		int color;
		final float phase = random.nextFloat() * TWO_PI;
		final float swaySpeed = 0.8f + random.nextFloat() * 1.2f;
		final float spinSpeed = (random.nextBoolean() ? 1 : -1) * (1.2f + random.nextFloat() * 2.2f);
		final float drag = 0.7f + random.nextFloat() * 0.6f;
		final float weight = 0.75f + random.nextFloat() * 0.5f;
		final float scale = 0.75f + random.nextFloat() * 0.5f;
		final float lift = 0.012f + random.nextFloat() * 0.02f; // leaves on the ground do not flicker into each other
	}
}
