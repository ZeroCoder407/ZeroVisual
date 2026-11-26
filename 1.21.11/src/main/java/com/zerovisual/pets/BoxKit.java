package com.zerovisual.pets;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shaded solid boxes and quads for the procedural pets; same flat lighting as the star pet. */
public final class BoxKit {
	private static final Vector3f LIGHT = new Vector3f(0.35f, 0.9f, 0.45f).normalize();

	private BoxKit() {
	}

	/** Axis-aligned solid box in the local space of {@code pose}, flat shaded per face. */
	public static void box(VertexConsumer b, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
		quad(b, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, rgb);   // top (+y)
		quad(b, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, rgb);  // bottom
		quad(b, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, rgb);   // front (+z)
		quad(b, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, rgb);  // back (-z)
		quad(b, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, rgb);   // right (+x)
		quad(b, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, rgb);  // left (-x)
	}

	/** Solid quad, CCW seen from the side {@code (nx, ny, nz)} points to. */
	public static void quad(VertexConsumer b, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
							float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz, int rgb) {
		Vector3f n = new Vector3f(nx, ny, nz);
		pose.transformDirection(n).normalize();
		float light = 0.6f + 0.4f * Math.max(0, Math.abs(n.dot(LIGHT)));
		int c = shade(rgb, light);
		b.addVertex(pose, x0, y0, z0).setColor(c);
		b.addVertex(pose, x1, y1, z1).setColor(c);
		b.addVertex(pose, x2, y2, z2).setColor(c);
		b.addVertex(pose, x3, y3, z3).setColor(c);
	}

	/** Frustum: a box whose top face is scaled by {@code top} (1 = straight box, 0 = pyramid tip). */
	public static void taper(VertexConsumer b, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
							 float top, int rgb) {
		float cx = (x0 + x1) * 0.5f, cz = (z0 + z1) * 0.5f;
		float tx0 = cx + (x0 - cx) * top, tx1 = cx + (x1 - cx) * top;
		float tz0 = cz + (z0 - cz) * top, tz1 = cz + (z1 - cz) * top;
		quad(b, pose, x0, y1, z1, tx0, y1, tz1, tx1, y1, tz1, x1, y1, z1, 0, 1, 0, rgb);
		quad(b, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, rgb);
		quad(b, pose, x0, y0, z1, x1, y0, z1, tx1, y1, tz1, tx0, y1, tz1, 0, 0, 1, rgb);
		quad(b, pose, x1, y0, z0, x0, y0, z0, tx0, y1, tz0, tx1, y1, tz0, 0, 0, -1, rgb);
		quad(b, pose, x1, y0, z1, x1, y0, z0, tx1, y1, tz0, tx1, y1, tz1, 1, 0, 0, rgb);
		quad(b, pose, x0, y0, z0, x0, y0, z1, tx0, y1, tz1, tx0, y1, tz0, -1, 0, 0, rgb);
	}

	/** A flat blade: base edge from {@code a} to {@code b}, tip at {@code t}, a thin two-sided sheet. */
	public static void blade(VertexConsumer b, Matrix4f pose, float ax, float ay, float az, float bx, float by, float bz,
							 float tx, float ty, float tz, float thickness, int rgb) {
		// front and back faces of a triangle, offset by the thickness along the normal
		twoSidedTri(b, pose, ax, ay, az, bx, by, bz, tx, ty, tz, rgb);
		twoSidedTri(b, pose, ax, ay, az + thickness, bx, by, bz + thickness, tx, ty, tz + thickness, rgb);
	}

	private static void twoSidedTri(VertexConsumer b, Matrix4f pose, float ax, float ay, float az,
									float bx, float by, float bz, float tx, float ty, float tz, int rgb) {
		Vector3f u = new Vector3f(bx - ax, by - ay, bz - az);
		Vector3f v = new Vector3f(tx - ax, ty - ay, tz - az);
		Vector3f n = u.cross(v);
		if (n.lengthSquared() < 1e-9f) return;
		n.normalize();
		n.mul(0.5f);
		float shade = 0.55f + 0.45f * Math.max(0, Math.abs(n.x * LIGHT.x + n.y * LIGHT.y + n.z * LIGHT.z));
		int c = shade(rgb, shade);
		b.addVertex(pose, ax, ay, az).setColor(c);
		b.addVertex(pose, bx, by, bz).setColor(c);
		b.addVertex(pose, tx, ty, tz).setColor(c);
		b.addVertex(pose, tx, ty, tz).setColor(c);
		// back side
		b.addVertex(pose, bx, by, bz).setColor(c);
		b.addVertex(pose, ax, ay, az).setColor(c);
		b.addVertex(pose, tx, ty, tz).setColor(c);
		b.addVertex(pose, tx, ty, tz).setColor(c);
	}

	/** One shaded triangle as a quad with the last vertex repeated. */
	public static void tri(VertexConsumer b, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
						   float x2, float y2, float z2, int rgb) {
		Vector3f u = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
		Vector3f v = new Vector3f(x2 - x0, y2 - y0, z2 - z0);
		Vector3f n = u.cross(v).normalize();
		pose.transformDirection(n).normalize();
		float light = 0.6f + 0.4f * Math.max(0, Math.abs(n.dot(LIGHT)));
		int c = shade(rgb, light);
		b.addVertex(pose, x0, y0, z0).setColor(c);
		b.addVertex(pose, x1, y1, z1).setColor(c);
		b.addVertex(pose, x2, y2, z2).setColor(c);
		b.addVertex(pose, x2, y2, z2).setColor(c);
	}

	public static int shade(int rgb, float brightness) {
		if (brightness > 1) brightness = 1;
		int r = (int) (((rgb >> 16) & 0xFF) * brightness);
		int g = (int) (((rgb >> 8) & 0xFF) * brightness);
		int bl = (int) ((rgb & 0xFF) * brightness);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}
}
