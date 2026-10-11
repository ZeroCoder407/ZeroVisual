package com.zerovisual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.awt.Color;

/** ARGB color, edited in the GUI as hue / saturation / brightness. */
public class ColorSetting extends Setting<Integer> {
	public ColorSetting(String name, int defaultArgb) {
		super(name, defaultArgb);
	}

	public float[] getHsb() {
		int c = get();
		return Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
	}

	public void setHsb(float hue, float saturation, float brightness) {
		int alpha = get() & 0xFF000000;
		set(alpha | (Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(String.format("#%08X", get()));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (!json.isJsonPrimitive()) return;
		String s = json.getAsString().replace("#", "");
		try {
			set((int) Long.parseLong(s, 16));
		} catch (NumberFormatException ignored) {
		}
	}
}
