package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;

/** Raises lightmap brightness above the vanilla gamma limit. Applied in LightmapTextureManagerMixin. */
public class FullBright extends Module {
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 10, 1, 15, 0.5));

	public FullBright() {
		super("FullBright", "Яркость без ограничений: видно в темноте", Category.VISUALS);
	}
}
