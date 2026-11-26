package com.zerovisual.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

/**
 * An immutable vector with the method names of the newer versions' Vec3d, so the effect code is shared.
 * 1.8.9's own Vec3 has other names (xCoord, addVector) and is converted with {@link #of(Vec3)}.
 */
public final class Vec3d {
	public static final Vec3d ZERO = new Vec3d(0, 0, 0);

	public final double x, y, z;

	public Vec3d(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public static Vec3d of(Vec3 v) {
		return new Vec3d(v.xCoord, v.yCoord, v.zCoord);
	}

	/** Where the entity is now. */
	public static Vec3d pos(Entity e) {
		return new Vec3d(e.posX, e.posY, e.posZ);
	}

	/** Between the last tick and now, as the entity is drawn. */
	public static Vec3d lerpedPos(Entity e, float partialTicks) {
		return new Vec3d(e.lastTickPosX + (e.posX - e.lastTickPosX) * partialTicks,
			e.lastTickPosY + (e.posY - e.lastTickPosY) * partialTicks,
			e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partialTicks);
	}

	public static Vec3d velocity(Entity e) {
		return new Vec3d(e.motionX, e.motionY, e.motionZ);
	}

	/** The look direction for a pitch and yaw in degrees, as Entity#getVectorForRotation. */
	public static Vec3d fromPolar(float pitch, float yaw) {
		double y = Math.toRadians(-yaw) - Math.PI, p = Math.toRadians(-pitch);
		double cos = -Math.cos(p);
		return new Vec3d(Math.sin(y) * cos, Math.sin(p), Math.cos(y) * cos);
	}

	public Vec3 toVanilla() {
		return new Vec3(x, y, z);
	}

	public Vec3d add(Vec3d o) {
		return new Vec3d(x + o.x, y + o.y, z + o.z);
	}

	public Vec3d add(double dx, double dy, double dz) {
		return new Vec3d(x + dx, y + dy, z + dz);
	}

	public Vec3d subtract(Vec3d o) {
		return new Vec3d(x - o.x, y - o.y, z - o.z);
	}

	public Vec3d subtract(double dx, double dy, double dz) {
		return new Vec3d(x - dx, y - dy, z - dz);
	}

	public Vec3d multiply(double k) {
		return new Vec3d(x * k, y * k, z * k);
	}

	public Vec3d multiply(double kx, double ky, double kz) {
		return new Vec3d(x * kx, y * ky, z * kz);
	}

	public Vec3d negate() {
		return new Vec3d(-x, -y, -z);
	}

	public double dotProduct(Vec3d o) {
		return x * o.x + y * o.y + z * o.z;
	}

	public Vec3d crossProduct(Vec3d o) {
		return new Vec3d(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
	}

	public double lengthSquared() {
		return x * x + y * y + z * z;
	}

	public double length() {
		return Math.sqrt(lengthSquared());
	}

	public double horizontalLength() {
		return Math.sqrt(x * x + z * z);
	}

	public Vec3d normalize() {
		double l = length();
		return l < 1e-4 ? ZERO : new Vec3d(x / l, y / l, z / l);
	}

	public double squaredDistanceTo(Vec3d o) {
		double dx = o.x - x, dy = o.y - y, dz = o.z - z;
		return dx * dx + dy * dy + dz * dz;
	}

	public double distanceTo(Vec3d o) {
		return Math.sqrt(squaredDistanceTo(o));
	}

	public Vec3d lerp(Vec3d to, double t) {
		return new Vec3d(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t);
	}

	public Vec3d rotateY(float angle) {
		double c = Math.cos(angle), s = Math.sin(angle);
		return new Vec3d(x * c + z * s, y, z * c - x * s);
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ")";
	}
}
