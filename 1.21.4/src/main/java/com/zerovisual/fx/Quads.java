package com.zerovisual.fx;

import org.joml.Matrix4f;

/** Takes colored quads, four vertices each. Every game version feeds it into its own renderer. */
@FunctionalInterface
public interface Quads {
	void vertex(Matrix4f m, float x, float y, float z, int argb);

	default void triangle(Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, int c0, int c1, int c2) {
		vertex(m, x0, y0, z0, c0);
		vertex(m, x1, y1, z1, c1);
		vertex(m, x2, y2, z2, c2);
		vertex(m, x2, y2, z2, c2);
	}

	/** A line of the given width in the XY plane of {@code m}. */
	default void line(Matrix4f m, float x0, float y0, float x1, float y1, float width, int c0, int c1) {
		float dx = x1 - x0, dy = y1 - y0;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 1e-5f) return;
		float nx = -dy / length * width / 2, ny = dx / length * width / 2;
		vertex(m, x0 + nx, y0 + ny, 0, c0);
		vertex(m, x0 - nx, y0 - ny, 0, c0);
		vertex(m, x1 - nx, y1 - ny, 0, c1);
		vertex(m, x1 + nx, y1 + ny, 0, c1);
	}
}
