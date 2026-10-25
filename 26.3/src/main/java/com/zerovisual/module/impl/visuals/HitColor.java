package com.zerovisual.module.impl.visuals;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;

/** Changes the red tint of damaged entities. Applied in OverlayTextureMixin. */
public class HitColor extends Module {
	/** Vanilla hurt overlay color (ARGB). */
	public static final int VANILLA = 0xB3FF0000;

	public final BooleanSetting useAccent = add(new BooleanSetting("Accent color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF8B5CF6)
		.visibleWhen(() -> !useAccent.get()));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.7, 0.1, 1, 0.05));

	public HitColor() {
		super("HitColor", "Entity hurt tint color", Category.VISUALS);
	}

	/** Overlay color to upload, or the vanilla one while disabled. */
	public int getOverlayColor() {
		if (!isEnabled()) return VANILLA;
		int base = useAccent.get() ? Theme.accent() : color.get();
		return ColorUtil.withAlpha(base, opacity.getFloat());
	}
}
