package com.zerovisual.util;

/** Math helpers of the newer versions' MathHelper that 1.8.9 lacks. */
public final class Mth {
	private Mth() {
	}

	public static float lerp(float t, float from, float to) {
		return from + t * (to - from);
	}

	public static double lerp(double t, double from, double to) {
		return from + t * (to - from);
	}

	public static float clamp(float value, float min, float max) {
		return value < min ? min : value > max ? max : value;
	}

	public static double clamp(double value, double min, double max) {
		return value < min ? min : value > max ? max : value;
	}

	public static int clamp(int value, int min, int max) {
		return value < min ? min : value > max ? max : value;
	}

	/** Between two angles in degrees along the shorter way. */
	public static float lerpAngle(float t, float from, float to) {
		float d = ((to - from) % 360 + 540) % 360 - 180;
		return from + t * d;
	}
}
