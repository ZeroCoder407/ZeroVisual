package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;

/** "Stretched resolution": renders the world with another aspect ratio. Applied in GameRendererMixin. */
public class AspectRatio extends Module {
	public final NumberSetting ratio = add(new NumberSetting("Ratio", 1.33, 0.8, 2.4, 0.01));

	public AspectRatio() {
		super("AspectRatio", "Растянутое изображение мира (как 4:3 stretched)", Category.VISUALS);
	}
}
