package com.zerovisual.render;

public final class ColorUtil {
	private ColorUtil() {
	}

	public static int withAlpha(int argb, float alpha) {
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	/** Multiplies the existing alpha of the color. */
	public static int fade(int argb, float factor) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, factor)));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	public static int lerp(int from, int to, float t) {
		t = Math.max(0, Math.min(1, t));
		int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Shifts brightness: factor > 1 lightens, < 1 darkens. */
	/** Moves an opaque color through its lightness: black at 0, the color itself at 0.5, white at 1. */
	public static int lightness(int rgb, float lightness) {
		int color = rgb | 0xFF000000;
		return lightness < 0.5f ? lerp(0xFF000000, color, lightness * 2) : lerp(color, 0xFFFFFFFF, lightness * 2 - 1);
	}

	public static int brighten(int argb, float factor) {
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
		int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
		int b = Math.min(255, Math.round((argb & 0xFF) * factor));
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}
}
