package com.zerovisual.module.impl.visuals;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;

/** Tints the sky and fog. Applied in BackgroundRendererMixin and ClientWorldMixin. */
public class Ambience extends Module {
	public final BooleanSetting useAccent = add(new BooleanSetting("Accent color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF6E64FA)
		.visibleWhen(() -> !useAccent.get()));
	public final NumberSetting skyStrength = add(new NumberSetting("Sky", 0.5, 0, 1, 0.05));
	public final NumberSetting fogStrength = add(new NumberSetting("Fog", 0.4, 0, 1, 0.05));

	public Ambience() {
		super("Ambience", "Sky and fog color", Category.VISUALS);
	}

	private int tint() {
		return useAccent.get() ? Theme.accent() : color.get();
	}

	public int tintSky(int rgb) {
		return ColorUtil.lerp(rgb | 0xFF000000, tint() | 0xFF000000, skyStrength.getFloat()) & 0xFFFFFF | (rgb & 0xFF000000);
	}

	/** Mixes the fog color (components 0..1) towards the tint. */
	public float[] tintFog(float r, float g, float b) {
		int c = tint();
		float s = fogStrength.getFloat();
		return new float[]{
			r + (((c >> 16) & 0xFF) / 255f - r) * s,
			g + (((c >> 8) & 0xFF) / 255f - g) * s,
			b + ((c & 0xFF) / 255f - b) * s
		};
	}
}
