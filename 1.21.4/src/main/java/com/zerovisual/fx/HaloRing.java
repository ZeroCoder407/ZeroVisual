package com.zerovisual.fx;

import com.zerovisual.render.ColorUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The halo as a solid ring with a round cross section, lit from above; shared by every version. */
public final class HaloRing {
	private static final Vector3f LIGHT = new Vector3f(0.35f, 1f, 0.45f).normalize();
	private static final int AROUND = 48, SIDES = 12;

	private HaloRing() {
	}

	/**
	 * The quads of the ring in the XY plane of {@code m}, around its origin. The colors run between {@code dark}
	 * and {@code light} around the ring and move with the time.
	 */
	public static void emit(Quads out, Matrix4f m, float radius, float tube, int light, int dark) {
		Vector3f n = new Vector3f();
		float phase = (float) (System.currentTimeMillis() % 1_000_000L / 600.0);
		for (int i = 0; i < AROUND; i++) {
			for (int j = 0; j < SIDES; j++) {
				corner(out, m, n, i, j, radius, tube, light, dark, phase);
				corner(out, m, n, i + 1, j, radius, tube, light, dark, phase);
				corner(out, m, n, i + 1, j + 1, radius, tube, light, dark, phase);
				corner(out, m, n, i, j + 1, radius, tube, light, dark, phase);
			}
		}
	}

	private static void corner(Quads out, Matrix4f m, Vector3f n, int i, int j, float radius, float tube, int light, int dark, float phase) {
		float u = (float) (i * 2 * Math.PI / AROUND), v = (float) (j * 2 * Math.PI / SIDES);
		float cu = (float) Math.cos(u), su = (float) Math.sin(u), cv = (float) Math.cos(v), sv = (float) Math.sin(v);
		// the normal turned with the matrix, to light the ring in world space
		m.transformDirection(n.set(cu * cv, su * cv, sv)).normalize();
		float shade = 0.55f + 0.45f * Math.max(0, n.dot(LIGHT));
		int base = ColorUtil.lerp(dark, light, 0.5f + 0.5f * (float) Math.sin(u + phase));
		// a little white on the lit side, as if the ring glowed
		int highlight = Math.round(40 * shade * shade);
		int r = Math.min(255, Math.round((base >> 16 & 255) * shade) + highlight);
		int g = Math.min(255, Math.round((base >> 8 & 255) * shade) + highlight);
		int b = Math.min(255, Math.round((base & 255) * shade) + highlight);
		out.vertex(m, (radius + tube * cv) * cu, (radius + tube * cv) * su, tube * sv, 0xFF000000 | r << 16 | g << 8 | b);
	}
}
