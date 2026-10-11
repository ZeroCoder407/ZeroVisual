package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;

/** Hides rain, snow and thunder on your client. Applied in WorldMixin. */
public class NoWeather extends Module {
	public NoWeather() {
		super("NoWeather", "Убирает дождь, снег и грозу (только у вас)", Category.VISUALS);
	}
}
