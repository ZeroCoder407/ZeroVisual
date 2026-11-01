package com.zerovisual.module.impl.hud;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;

/** The look of every HUD card: background, outline, corners and shadow. Read by Theme. */
public class HudStyle extends Module {
	public final ColorSetting background = add(new ColorSetting("Background", 0xFF0D0D12));
	public final NumberSetting lightness = add(new NumberSetting("Lightness", 0.5, 0, 1, 0.02));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.88, 0, 1, 0.02));
	public final NumberSetting radius = add(new NumberSetting("Radius", 6, 0, 12, 1));
	public final BooleanSetting shadow = add(new BooleanSetting("Shadow", true));
	public final BooleanSetting outline = add(new BooleanSetting("Outline", false));
	public final BooleanSetting outlineAccent = add(new BooleanSetting("Outline accent", true)
		.visibleWhen(outline::get));
	public final ColorSetting outlineColor = add(new ColorSetting("Outline color", 0xFFFFFFFF)
		.visibleWhen(() -> outline.get() && !outlineAccent.get()));
	public final NumberSetting outlineWidth = add(new NumberSetting("Outline width", 1, 0.5, 3, 0.25)
		.visibleWhen(outline::get));

	public HudStyle() {
		super("HudStyle", "Background, outline and corners of all HUD cards", Category.HUD);
	}

	@Override
	public boolean isToggleable() {
		return false;
	}
}
