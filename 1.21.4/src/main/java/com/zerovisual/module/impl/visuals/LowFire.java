package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;

/** Lowers the first-person fire overlay. Applied in InGameOverlayRendererMixin. */
public class LowFire extends Module {
	public final NumberSetting offset = add(new NumberSetting("Offset", 0.3, 0.05, 0.6, 0.05));

	public LowFire() {
		super("LowFire", "Lowers the fire overlay when you are burning", Category.VISUALS);
	}
}
