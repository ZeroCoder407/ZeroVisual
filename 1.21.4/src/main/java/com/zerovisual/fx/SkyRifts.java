package com.zerovisual.fx;

import com.zerovisual.render.ColorUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The ordinary sky cracked open, with depth inside: each crack has walls going down in layers, one side lit and the
 * other in shadow. Deeper layers shift with the view, so turning the camera opens one wall and hides the other, like
 * looking into a real gap. Mirror shards drift on the far side, broken glass of the sky hangs along the edges.
 */
public final class SkyRifts {
	private static final float DISTANCE = 39;
	private static final int STEPS = 44;
	/** Layers of the walls, from the rim down to the floor. */
	private static final int LAYERS = 6;
	/** How far the floor may shift sideways, as a share of the half width. */
	private static final float PARALLAX = 0.72f;

	private record Shard(Vector3f a, Vector3f b, Vector3f c, float seed) {
	}

	/** One crack: its spine and half width at every step, as points on the sky sphere. */
	private record Rift(Vector3f center, Vector3f[] spine, Vector3f[] side, float[] width, float seed, List<Shard> inside, List<Shard> glass) {
	}

	private final List<Rift> rifts = new ArrayList<>();
	/** Scratch: sideways shift of the layers at each step. */
	private final float[] shift = new float[STEPS + 1];
	/** Colors from the rim down to the floor. */
	private int rimColor = 0xFF8B5CF6, midColor = 0xFF22D3EE, deepColor = 0xFFFF4D8D;

	public void setColors(int rim, int mid, int deep) {
		rimColor = rim;
		midColor = mid;
		deepColor = deep;
	}

	/** The color of the walls at depth k, 0 at the rim and 1 at the floor. */
	private int depthColor(float k) {
		return k < 0.5f ? ColorUtil.lerp(rimColor, midColor, k * 2) : ColorUtil.lerp(midColor, deepColor, (k - 0.5f) * 2);
	}

	public SkyRifts() {
		Random random = new Random(7719);
		float[][] places = {
			{0.72f, 0.42f, -0.55f}, {-0.6f, 0.62f, -0.5f}, {0.05f, 0.82f, 0.56f}, {-0.82f, 0.28f, 0.5f},
			{0.62f, 0.3f, 0.72f}, {0.02f, 0.46f, -0.89f}, {-0.18f, 0.97f, -0.1f}
		};
		for (float[] place : places) {
			rifts.add(rift(new Vector3f(place[0], place[1], place[2]).normalize(), 0.5f + random.nextFloat() * 0.4f,
				0.05f + random.nextFloat() * 0.055f, random, true));
			// a thin branch splits off each big crack
			Vector3f near = new Vector3f(place[0], place[1], place[2]).normalize()
				.add(random.nextFloat() * 0.3f - 0.15f, random.nextFloat() * 0.3f - 0.15f, random.nextFloat() * 0.3f - 0.15f).normalize();
			rifts.add(rift(near, 0.18f + random.nextFloat() * 0.16f, 0.014f, random, false));
		}
	}

	private static Rift rift(Vector3f center, float length, float width, Random random, boolean big) {
		Vector3f u = new Vector3f(center).cross(0, 1, 0).normalize();
		Vector3f v = new Vector3f(center).cross(u).normalize();
		float tilt = random.nextFloat() * 6.28f;
		Vector3f along = new Vector3f(u).mul((float) Math.cos(tilt)).add(new Vector3f(v).mul((float) Math.sin(tilt)));
		Vector3f across = new Vector3f(center).cross(along).normalize();
		Vector3f[] spine = new Vector3f[STEPS + 1], side = new Vector3f[STEPS + 1];
		float[] widths = new float[STEPS + 1];
		float drift = 0;
		for (int i = 0; i <= STEPS; i++) {
			float s = (float) i / STEPS;
			// a jagged walk: long straight runs broken by sudden turns, like glass
			if (random.nextFloat() < 0.4f) drift += (random.nextFloat() - 0.5f) * length * 0.14f;
			spine[i] = new Vector3f(center).add(new Vector3f(along).mul((s - 0.5f) * length)).add(new Vector3f(across).mul(drift)).normalize().mul(DISTANCE);
			side[i] = new Vector3f(across);
			widths[i] = width * (float) Math.pow(Math.sin(s * Math.PI), 0.7) * (0.45f + random.nextFloat() * 1.1f) * DISTANCE;
		}
		List<Shard> inside = new ArrayList<>(), glass = new ArrayList<>();
		if (big) {
			for (int i = 0; i < 26; i++) {
				int at = 6 + random.nextInt(STEPS - 12);
				float offset = (random.nextFloat() - 0.5f) * 1.3f * widths[at];
				inside.add(shard(spine[at], side[at], along, offset, 0.35f + random.nextFloat() * 0.7f, random));
			}
			for (int i = 0; i < 16; i++) {
				int at = 4 + random.nextInt(STEPS - 8);
				float sign = random.nextBoolean() ? 1 : -1;
				glass.add(shard(spine[at], side[at], along, sign * (widths[at] + 0.25f + random.nextFloat() * 1.6f), 0.5f + random.nextFloat() * 1.1f, random));
			}
		}
		return new Rift(center, spine, side, widths, random.nextFloat() * 100, inside, glass);
	}

	private static Shard shard(Vector3f at, Vector3f side, Vector3f along, float offset, float size, Random random) {
		Vector3f mid = new Vector3f(at).add(new Vector3f(side).mul(offset));
		Vector3f[] corner = new Vector3f[3];
		float start = random.nextFloat() * 6.28f;
		for (int i = 0; i < 3; i++) {
			double angle = start + i * 2.09 + random.nextFloat() * 0.8f;
			float r = size * (0.6f + random.nextFloat() * 0.8f);
			corner[i] = new Vector3f(mid).add(new Vector3f(side).mul((float) Math.cos(angle) * r)).add(new Vector3f(along).mul((float) Math.sin(angle) * r));
		}
		return new Shard(corner[0], corner[1], corner[2], random.nextFloat() * 100);
	}

	/** Where the view points across each crack: the layers lean the other way. */
	private void updateShift(Rift rift, float lookX, float lookY, float lookZ) {
		float dx = lookX - rift.center.x, dy = lookY - rift.center.y, dz = lookZ - rift.center.z;
		for (int i = 0; i <= STEPS; i++) {
			Vector3f side = rift.side[i];
			float across = dx * side.x + dy * side.y + dz * side.z;
			shift[i] = Math.max(-1, Math.min(1, across * 2.6f)) * PARALLAX;
		}
	}

	/** Offset across the crack of a point on layer {@code k} (0 at the rim, 1 at the floor), on one side of it. */
	private float layerOffset(Rift rift, int i, float k, int sideSign) {
		float width = rift.width[i];
		// the walls close in as they go down, and the whole stack leans with the view
		return width * (sideSign * (1f - k * 0.62f) + shift[i] * k);
	}

	private static void put(Quads q, Matrix4f m, Vector3f spine, Vector3f side, float offset, int color) {
		q.vertex(m, spine.x + side.x * offset, spine.y + side.y * offset, spine.z + side.z * offset, color);
	}

	/** The far side and the walls, drawn with normal blending from the deepest layer up. */
	public void fill(Quads q, Matrix4f m, float lookX, float lookY, float lookZ, float time) {
		int floor = ColorUtil.lerp(0xFF000000, deepColor, 0.3f);
		for (Rift rift : rifts) {
			updateShift(rift, lookX, lookY, lookZ);
			// the sky seen through the crack is dark from rim to rim
			for (int i = 0; i < STEPS; i++) {
				put(q, m, rift.spine[i], rift.side[i], rift.width[i], floor);
				put(q, m, rift.spine[i], rift.side[i], -rift.width[i], floor);
				put(q, m, rift.spine[i + 1], rift.side[i + 1], -rift.width[i + 1], floor);
				put(q, m, rift.spine[i + 1], rift.side[i + 1], rift.width[i + 1], floor);
			}
			float tilt = (float) Math.sin(time * 0.25f + rift.seed);
			for (Shard shard : rift.inside) drawShard(q, m, rift, shard, tilt, time);
			for (int layer = LAYERS - 1; layer >= 0; layer--) {
				float k0 = (float) layer / LAYERS, k1 = (float) (layer + 1) / LAYERS;
				for (int sign = -1; sign <= 1; sign += 2) {
					// light falls from one side: one wall glows, the other stays in shadow
					float lit = sign > 0 ? 0.95f : 0.42f;
					int top = ColorUtil.lerp(0xFF000000, depthColor(k0), lit), bottom = ColorUtil.lerp(0xFF000000, depthColor(k1), lit);
					for (int i = 0; i < STEPS; i++) {
						put(q, m, rift.spine[i], rift.side[i], layerOffset(rift, i, k0, sign), top);
						put(q, m, rift.spine[i], rift.side[i], layerOffset(rift, i, k1, sign), bottom);
						put(q, m, rift.spine[i + 1], rift.side[i + 1], layerOffset(rift, i + 1, k1, sign), bottom);
						put(q, m, rift.spine[i + 1], rift.side[i + 1], layerOffset(rift, i + 1, k0, sign), top);
					}
				}
			}
			for (Shard shard : rift.glass) {
				int color = ColorUtil.withAlpha(0xFFDDEBFF, 0.2f + 0.1f * (float) Math.sin(time * 0.7f + shard.seed));
				q.triangle(m, shard.a.x, shard.a.y, shard.a.z, shard.b.x, shard.b.y, shard.b.z, shard.c.x, shard.c.y, shard.c.z, color, color, color);
			}
		}
	}

	/** A mirror shard drifting on the far side; it catches the light as it slowly turns. */
	private void drawShard(Quads q, Matrix4f m, Rift rift, Shard shard, float tilt, float time) {
		float glint = 0.5f + 0.5f * (float) Math.sin(time * 0.6f + shard.seed);
		int light = ColorUtil.lerp(ColorUtil.lerp(0xFF000000, midColor, 0.4f), ColorUtil.lerp(midColor, 0xFFFFFFFF, 0.55f), glint * glint);
		int dark = ColorUtil.lerp(ColorUtil.lerp(0xFF000000, deepColor, 0.2f), deepColor, glint * 0.7f);
		// shards lie deep, so they move with the view further than any wall
		float dx = rift.side[0].x * shift[STEPS / 2] * 1.4f * rift.width[STEPS / 2], dy = rift.side[0].y * shift[STEPS / 2] * 1.4f * rift.width[STEPS / 2],
			dz = rift.side[0].z * shift[STEPS / 2] * 1.4f * rift.width[STEPS / 2];
		float sway = tilt * 0.3f;
		q.triangle(m, shard.a.x + dx, shard.a.y + dy + sway, shard.a.z + dz, shard.b.x + dx, shard.b.y + dy, shard.b.z + dz,
			shard.c.x + dx, shard.c.y + dy - sway, shard.c.z + dz, light, dark, dark);
	}

	/** Light leaking out of the cracks, drawn with additive blending. It breathes slowly. */
	public void glow(Quads q, Matrix4f m, float time) {
		for (Rift rift : rifts) {
			float breathe = 0.72f + 0.28f * (float) Math.sin(time * 0.9f + rift.seed);
			halo(q, m, rift, ColorUtil.withAlpha(rimColor, 0.3f * breathe));
			rim(q, m, rift, ColorUtil.withAlpha(ColorUtil.lerp(rimColor, 0xFFFFFFFF, 0.75f), 0.95f * breathe));
			for (Shard shard : rift.glass) {
				int edge = ColorUtil.withAlpha(0xFFFFFFFF, 0.5f * breathe);
				edge(q, m, shard.a, shard.b, edge);
				edge(q, m, shard.b, shard.c, edge);
				edge(q, m, shard.c, shard.a, edge);
			}
		}
	}

	private static void edge(Quads q, Matrix4f m, Vector3f a, Vector3f b, int color) {
		Vector3f across = new Vector3f(b).sub(a).cross(a).normalize(0.035f);
		q.vertex(m, a.x + across.x, a.y + across.y, a.z + across.z, color);
		q.vertex(m, a.x - across.x, a.y - across.y, a.z - across.z, color);
		q.vertex(m, b.x - across.x, b.y - across.y, b.z - across.z, color);
		q.vertex(m, b.x + across.x, b.y + across.y, b.z + across.z, color);
	}

	/** A soft glow around the crack, outside its rim. */
	private static void halo(Quads q, Matrix4f m, Rift rift, int color) {
		int clear = color & 0x00FFFFFF;
		for (int i = 0; i < STEPS; i++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				float a = rift.width[i] * sign, b = rift.width[i + 1] * sign;
				put(q, m, rift.spine[i], rift.side[i], a, color);
				put(q, m, rift.spine[i], rift.side[i], a * 2.6f, clear);
				put(q, m, rift.spine[i + 1], rift.side[i + 1], b * 2.6f, clear);
				put(q, m, rift.spine[i + 1], rift.side[i + 1], b, color);
			}
		}
	}

	/** Thin bright lines along both edges of a crack. */
	private static void rim(Quads q, Matrix4f m, Rift rift, int color) {
		float thick = 0.09f;
		for (int i = 0; i < STEPS; i++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				float a = rift.width[i] * sign, b = rift.width[i + 1] * sign;
				put(q, m, rift.spine[i], rift.side[i], a, color);
				put(q, m, rift.spine[i], rift.side[i], a + thick * sign, color);
				put(q, m, rift.spine[i + 1], rift.side[i + 1], b + thick * sign, color);
				put(q, m, rift.spine[i + 1], rift.side[i + 1], b, color);
			}
		}
	}
}
