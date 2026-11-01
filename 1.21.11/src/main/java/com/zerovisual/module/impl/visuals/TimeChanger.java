package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;

/** Client-side time of day (only what you see). Applied in ClientWorldPropertiesMixin. */
public class TimeChanger extends Module {
	public final NumberSetting time = add(new NumberSetting("Time", 18000, 0, 24000, 250));

	public TimeChanger() {
		super("TimeChanger", "Your own time of day (only you see it)", Category.VISUALS);
	}
}
