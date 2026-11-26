package com.zerovisual.module.impl.visuals;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;

/**
 * Recolors the enchantment glint. 1.8.9 tints the glint texture of items with one color constant and the glint
 * of armor with a GL color (RenderItemMixin, LayerArmorBaseMixin); both are replaced by the color set here.
 */
public class GlintColor extends Module {
	public final BooleanSetting useAccent = add(new BooleanSetting("Accent color", false));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF38BDF8));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1, 0.3, 2, 0.1));
	public final BooleanSetting rainbow = add(new BooleanSetting("Rainbow", false));

	public GlintColor() {
		super("GlintColor", "Custom color of the enchantment glint", Category.VISUALS);
	}

	/** The module while it is on, for the mixins. */
	public static GlintColor active() {
		com.zerovisual.ZeroVisualClient client = com.zerovisual.ZeroVisualClient.get();
		if (client == null) return null;
		GlintColor glint = client.modules().get(GlintColor.class);
		return glint.isEnabled() ? glint : null;
	}

	private int base() {
		if (rainbow.get()) return java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 4000) / 4000f, 0.8f, 1f);
		return useAccent.get() ? Theme.accent() : color.get();
	}

	/** The ARGB tint of the item glint. */
	public int itemTint() {
		int c = base();
		float k = brightness.getFloat();
		int r = Math.min(255, Math.round((c >> 16 & 255) * k)), g = Math.min(255, Math.round((c >> 8 & 255) * k)), b = Math.min(255, Math.round((c & 255) * k));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** The armor glint color, {@code shade} being the vanilla flicker factor. */
	public float[] armorTint(float shade) {
		int c = itemTint();
		return new float[]{(c >> 16 & 255) / 255f * shade, (c >> 8 & 255) / 255f * shade, (c & 255) / 255f * shade};
	}
}
