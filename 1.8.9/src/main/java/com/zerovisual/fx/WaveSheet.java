package com.zerovisual.fx;

import com.zerovisual.render.ColorUtil;

/**
 * Block faces bent by a height field: the top of a block as a fine grid, so a wave stays round and smooth across
 * blocks, and strips of the side texture where the ground ends, so no raised edge hangs in the air.
 */
public final class WaveSheet {
	/** Cells per block side. */
	private static final int DIV = 4;
	private static final float STEP = 0.25f;
	/** Steeper than the real slope: the light on the slopes is what shows the shape of the wave. */
	private static final float SLOPE_LIGHT = 2.4f;
	/** Sides: toward -z, +x, +z, -x. */
	public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;

	/** Height of the sheet above the blocks at a world position. */
	@FunctionalInterface
	public interface Heights {
		float at(float x, float z);
	}

	@FunctionalInterface
	public interface Sink {
		void vertex(float x, float y, float z, int color, float u, float v, float nx, float ny, float nz);
	}

	private WaveSheet() {
	}

	private static void point(Sink out, float x, float y, float z, float wx, float wz, float cx, float cz, float u, float v, int color, Heights h) {
		float dx = h.at(wx + cx + STEP, wz + cz) - h.at(wx + cx - STEP, wz + cz);
		float dz = h.at(wx + cx, wz + cz + STEP) - h.at(wx + cx, wz + cz - STEP);
		float nx = -dx * SLOPE_LIGHT, nz = -dz * SLOPE_LIGHT, length = (float) Math.sqrt(nx * nx + 1 + nz * nz);
		float shade = 1f - Math.min(0.3f, (Math.abs(dx) + Math.abs(dz)) * 0.55f);
		out.vertex(x + cx, y + h.at(wx + cx, wz + cz), z + cz, ColorUtil.lerp(color & 0xFF000000, color, shade), u, v, nx / length, 1 / length, nz / length);
	}

	/**
	 * The top face.
	 * @param x, y, z camera-relative corner of the face
	 * @param wx, wz the same corner in the world
	 */
	public static void top(Sink out, float x, float y, float z, float wx, float wz, float u0, float v0, float u1, float v1, int color, Heights h) {
		float du = (u1 - u0) / DIV, dv = (v1 - v0) / DIV;
		for (int i = 0; i < DIV; i++) {
			for (int j = 0; j < DIV; j++) {
				float a = i * STEP, b = j * STEP, c = a + STEP, d = b + STEP;
				// cells that did not move stay the real block
				if (h.at(wx + a, wz + b) + h.at(wx + c, wz + b) + h.at(wx + c, wz + d) + h.at(wx + a, wz + d) < 0.004f) continue;
				point(out, x, y, z, wx, wz, a, d, u0 + i * du, v0 + (j + 1) * dv, color, h);
				point(out, x, y, z, wx, wz, c, d, u0 + (i + 1) * du, v0 + (j + 1) * dv, color, h);
				point(out, x, y, z, wx, wz, c, b, u0 + (i + 1) * du, v0 + j * dv, color, h);
				point(out, x, y, z, wx, wz, a, b, u0 + i * du, v0 + j * dv, color, h);
			}
		}
	}

	/** A strip of the side texture under one edge of the top, as tall as the sheet is high there. */
	public static void side(Sink out, int side, float x, float y, float z, float wx, float wz, float u0, float v0, float u1, float v1, int color, Heights h) {
		float nx = side == EAST ? 1 : side == WEST ? -1 : 0, nz = side == SOUTH ? 1 : side == NORTH ? -1 : 0;
		for (int i = 0; i < DIV; i++) {
			float a = i * STEP, b = a + STEP;
			float ax = side == EAST ? 1 : side == WEST ? 0 : a, az = side == SOUTH ? 1 : side == NORTH ? 0 : a;
			float bx = side == EAST ? 1 : side == WEST ? 0 : b, bz = side == SOUTH ? 1 : side == NORTH ? 0 : b;
			float ha = h.at(wx + ax, wz + az), hb = h.at(wx + bx, wz + bz);
			if (ha + hb < 0.004f) continue;
			float ua = u0 + (u1 - u0) * a, ub = u0 + (u1 - u0) * b;
			float vt = v0, va = v0 + (v1 - v0) * Math.min(1, ha), vb = v0 + (v1 - v0) * Math.min(1, hb);
			// both windings: the strip is seen from outside whichever way the renderer culls
			out.vertex(x + ax, y + ha, z + az, color, ua, vt, nx, 0, nz);
			out.vertex(x + ax, y, z + az, color, ua, va, nx, 0, nz);
			out.vertex(x + bx, y, z + bz, color, ub, vb, nx, 0, nz);
			out.vertex(x + bx, y + hb, z + bz, color, ub, vt, nx, 0, nz);
			out.vertex(x + bx, y + hb, z + bz, color, ub, vt, nx, 0, nz);
			out.vertex(x + bx, y, z + bz, color, ub, vb, nx, 0, nz);
			out.vertex(x + ax, y, z + az, color, ua, va, nx, 0, nz);
			out.vertex(x + ax, y + ha, z + az, color, ua, vt, nx, 0, nz);
		}
	}
}
