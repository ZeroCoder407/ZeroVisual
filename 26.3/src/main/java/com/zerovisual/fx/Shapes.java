package com.zerovisual.fx;

import com.zerovisual.render.ColorUtil;
import org.joml.Matrix4f;

/** Geometry of the effects that look the same in every game version. */
public final class Shapes {
	/** Dragon wing, x outwards and y up, root at (0, 0). The wrist carries the fingers, the membrane hangs between them. */
	private static final float WRIST_X = 0.40f, WRIST_Y = 0.56f;
	private static final float[][] FINGERS = {{1.08f, 0.60f}, {1.12f, 0.10f}, {0.92f, -0.34f}, {0.56f, -0.62f}};
	private static final float[] TAIL = {0.10f, -0.30f};
	private static final int SCALLOP = 8;

	private Shapes() {
	}

	/** Eight-sided crystal around the origin of {@code m}, standing on its tip. */
	public static void crystal(Quads q, Matrix4f m, float r, int top, int bottom) {
		float h = r * 1.7f;
		float[][] rim = {{r, 0}, {0, r}, {-r, 0}, {0, -r}};
		for (int i = 0; i < 4; i++) {
			float[] a = rim[i], b = rim[(i + 1) % 4];
			// neighbouring faces differ a little, so the crystal reads as faceted
			int upper = i % 2 == 0 ? top : ColorUtil.lerp(top, bottom, 0.35f);
			int lower = i % 2 == 0 ? ColorUtil.lerp(top, bottom, 0.5f) : bottom;
			q.triangle(m, 0, h, 0, a[0], 0, a[1], b[0], 0, b[1], upper, upper, upper);
			q.triangle(m, 0, -h, 0, b[0], 0, b[1], a[0], 0, a[1], lower, lower, lower);
		}
	}

	/** One heartbeat over u in 0..1, like a hospital monitor. */
	private static float beat(float u) {
		if (u < 0.34f) return 0;
		if (u < 0.40f) return (u - 0.34f) / 0.06f * 0.16f;
		if (u < 0.44f) return 0.16f - (u - 0.40f) / 0.04f * 0.32f;
		if (u < 0.49f) return -0.16f + (u - 0.44f) / 0.05f * 1.16f;
		if (u < 0.55f) return 1f - (u - 0.49f) / 0.06f * 1.5f;
		if (u < 0.60f) return -0.5f + (u - 0.55f) / 0.05f * 0.5f;
		return 0;
	}

	/**
	 * A pulse line running across the XY plane of {@code m}.
	 * @param kick 0 at rest, 1 right after a hit: the line swings far harder and shivers
	 */
	public static void pulse(Quads q, Matrix4f m, float width, float height, float time, float kick, int color) {
		int points = 72;
		float amplitude = height * (0.45f + kick * 1.6f);
		float px = 0, py = 0;
		for (int i = 0; i <= points; i++) {
			float k = (float) i / points;
			float x = (k - 0.5f) * width;
			float u = k * 2.4f - time * 0.9f;
			u -= (float) Math.floor(u);
			float y = beat(u) * amplitude;
			y += (float) Math.sin(k * 90 + time * 40) * height * 0.11f * kick;
			float edge = Math.min(1, Math.min(k, 1 - k) * 6);
			if (i > 0) q.line(m, px, py, x, y, 0.028f + kick * 0.022f, ColorUtil.withAlpha(color, edge), ColorUtil.withAlpha(color, edge));
			px = x;
			py = y;
		}
	}

	private static float scallopX(float[] a, float[] b, float t) {
		float mid = (a[0] + b[0]) / 2 + (WRIST_X - (a[0] + b[0]) / 2) * 0.32f;
		return (1 - t) * (1 - t) * a[0] + 2 * t * (1 - t) * mid + t * t * b[0];
	}

	private static float scallopY(float[] a, float[] b, float t) {
		float mid = (a[1] + b[1]) / 2 + (WRIST_Y - (a[1] + b[1]) / 2) * 0.32f;
		return (1 - t) * (1 - t) * a[1] + 2 * t * (1 - t) * mid + t * t * b[1];
	}

	/** Dragon wing: a leathery membrane scalloped between the finger bones, with a claw on the wrist. */
	public static void dragonWing(Quads q, Matrix4f m, int membrane, int shade, int bone) {
		float[][] edge = new float[FINGERS.length + 1][];
		System.arraycopy(FINGERS, 0, edge, 0, FINGERS.length);
		edge[FINGERS.length] = TAIL;
		for (int f = 0; f + 1 < edge.length; f++) {
			float[] a = edge[f], b = edge[f + 1];
			for (int i = 0; i < SCALLOP; i++) {
				float t0 = (float) i / SCALLOP, t1 = (float) (i + 1) / SCALLOP;
				q.triangle(m, WRIST_X, WRIST_Y, 0, scallopX(a, b, t0), scallopY(a, b, t0), 0, scallopX(a, b, t1), scallopY(a, b, t1), 0,
					shade, membrane, membrane);
			}
		}
		// the part of the membrane between the arm and the body
		q.triangle(m, 0, 0, 0, WRIST_X, WRIST_Y, 0, TAIL[0], TAIL[1], 0, shade, shade, membrane);

		q.line(m, 0, 0, WRIST_X, WRIST_Y, 0.05f, bone, bone);
		for (float[] finger : FINGERS) {
			float midX = WRIST_X + (finger[0] - WRIST_X) * 0.5f, midY = WRIST_Y + (finger[1] - WRIST_Y) * 0.5f + 0.03f;
			q.line(m, WRIST_X, WRIST_Y, midX, midY, 0.032f, bone, bone);
			q.line(m, midX, midY, finger[0], finger[1], 0.016f, bone, bone);
		}
		q.triangle(m, WRIST_X - 0.04f, WRIST_Y, 0, WRIST_X + 0.04f, WRIST_Y, 0, WRIST_X - 0.02f, WRIST_Y + 0.17f, 0, bone, bone, bone);
	}
}
