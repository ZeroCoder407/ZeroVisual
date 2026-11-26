package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;

/** Position and size of the held items in first person. Applied in HeldItemRendererMixin. */
public class ViewModel extends Module {
	public final NumberSetting x = add(new NumberSetting("X", 0, -1, 1, 0.05));
	public final NumberSetting y = add(new NumberSetting("Y", 0, -1, 1, 0.05));
	public final NumberSetting z = add(new NumberSetting("Z", 0, -1, 1, 0.05));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1, 0.3, 1.5, 0.05));

	public ViewModel() {
		super("ViewModel", "Position and size of held items", Category.VISUALS);
	}
}
