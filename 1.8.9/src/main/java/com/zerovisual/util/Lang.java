package com.zerovisual.util;

import net.minecraft.client.resources.I18n;

/** The translations of the mod (assets/zerovisual/lang/*.lang), with a check for missing keys that 1.8.9 lacks. */
public final class Lang {
	private Lang() {
	}

	public static String get(String key, Object... args) {
		return I18n.format(key, args);
	}

	public static boolean has(String key) {
		return !I18n.format(key).equals(key);
	}

	/** The translation, or {@code fallback} while the key is missing. */
	public static String orElse(String key, String fallback, Object... args) {
		return has(key) ? get(key, args) : String.format(fallback, args);
	}
}
