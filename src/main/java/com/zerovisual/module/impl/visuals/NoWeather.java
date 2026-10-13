package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;

/** Hides rain, snow and thunder on your client. Applied in WorldMixin. */
public class NoWeather extends Module {
	public NoWeather() {
		super("NoWeather", "Removes rain, snow and thunder (only for you)", Category.VISUALS);
	}
}
