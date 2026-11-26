package com.zerovisual.pets;

import com.zerovisual.fx.Quads;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shaded solid boxes for the effects; every face is emitted with both windings so no culling can hide it. */
public final class BoxKit {
	private static final Vector3f LIGHT = new Vector3f(0.35f, 0.9f, 0.45f).normalize();

	private BoxKit() {
	}

	public static void box(Quads q, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
		quad(q, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, rgb);
		quad(q, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, rgb);
		quad(q, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, rgb);
		quad(q, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, rgb);
		quad(q, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, rgb);
		quad(q, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, rgb);
	}

	public static void taper(Quads q, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
							 float top, int rgb) {
		float cx = (x0 + x1) * 0.5f, cz = (z0 + z1) * 0.5f;
		float tx0 = cx + (x0 - cx) * top, tx1 = cx + (x1 - cx) * top;
		float tz0 = cz + (z0 - cz) * top, tz1 = cz + (z1 - cz) * top;
		quad(q, pose, x0, y1, z1, tx0, y1, tz1, tx1, y1, tz1, x1, y1, z1, 0, 1, 0, rgb);
		quad(q, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, rgb);
		quad(q, pose, x0, y0, z1, x1, y0, z1, tx1, y1, tz1, tx0, y1, tz1, 0, 0, 1, rgb);
		quad(q, pose, x1, y0, z0, x0, y0, z0, tx0, y1, tz0, tx1, y1, tz0, 0, 0, -1, rgb);
		quad(q, pose, x1, y0, z1, x1, y0, z0, tx1, y1, tz0, tx1, y1, tz1, 1, 0, 0, rgb);
		quad(q, pose, x0, y0, z0, x0, y0, z1, tx0, y1, tz1, tx0, y1, tz0, -1, 0, 0, rgb);
	}

	public static void quad(Quads q, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
							float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz, int rgb) {
		Vector3f n = new Vector3f(nx, ny, nz);
		pose.transformDirection(n).normalize();
		float light = 0.6f + 0.4f * Math.max(0, Math.abs(n.dot(LIGHT)));
		int c = shade(rgb, light);
		q.vertex(pose, x0, y0, z0, c);
		q.vertex(pose, x1, y1, z1, c);
		q.vertex(pose, x2, y2, z2, c);
		q.vertex(pose, x3, y3, z3, c);
		q.vertex(pose, x3, y3, z3, c);
		q.vertex(pose, x2, y2, z2, c);
		q.vertex(pose, x1, y1, z1, c);
		q.vertex(pose, x0, y0, z0, c);
	}

	public static void tri(Quads q, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
						   float x2, float y2, float z2, int rgb) {
		Vector3f u = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
		Vector3f v = new Vector3f(x2 - x0, y2 - y0, z2 - z0);
		Vector3f n = u.cross(v).normalize();
		pose.transformDirection(n).normalize();
		float light = 0.6f + 0.4f * Math.max(0, Math.abs(n.dot(LIGHT)));
		int c = shade(rgb, light);
		q.vertex(pose, x0, y0, z0, c);
		q.vertex(pose, x1, y1, z1, c);
		q.vertex(pose, x2, y2, z2, c);
		q.vertex(pose, x2, y2, z2, c);
		q.vertex(pose, x2, y2, z2, c);
		q.vertex(pose, x1, y1, z1, c);
		q.vertex(pose, x0, y0, z0, c);
		q.vertex(pose, x0, y0, z0, c);
	}

	public static int shade(int rgb, float brightness) {
		if (brightness > 1) brightness = 1;
		int r = (int) (((rgb >> 16) & 0xFF) * brightness);
		int g = (int) (((rgb >> 8) & 0xFF) * brightness);
		int bl = (int) ((rgb & 0xFF) * brightness);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}
}
