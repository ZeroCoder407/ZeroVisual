package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;

/**
 * Recolors the enchantment glint. The glint shader multiplies its purple texture by ColorModulator,
 * so the modulator is the wanted color divided by the texture's own color (RenderPhaseMixin).
 */
public class GlintColor extends Module {
	/** Brightest channels of the vanilla glint texture (157, 80, 249), normalized. */
	private static final float TEX_R = 157 / 249f, TEX_G = 80 / 249f, TEX_B = 1f;

	public final BooleanSetting useAccent = add(new BooleanSetting("Accent color", false));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF38BDF8));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1, 0.3, 2, 0.1));
	public final BooleanSetting rainbow = add(new BooleanSetting("Rainbow", false));

	private boolean applied;

	public GlintColor() {
		super("GlintColor", "Custom color of the enchantment glint", Category.VISUALS);
	}

	/** Glint texturing was set up: tint it. */
	public void apply() {
		int c;
		if (rainbow.get()) c = java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 4000) / 4000f, 0.8f, 1f);
		else c = useAccent.get() ? Theme.accent() : color.get();
		float k = brightness.getFloat();
		float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
		RenderSystem.setShaderColor(r / TEX_R * k, g / TEX_G * k, b / TEX_B * k, 1f);
		applied = true;
	}

	/** Glint texturing was cleared: restore the shader color. */
	public void reset() {
		if (!applied) return;
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		applied = false;
	}
}
