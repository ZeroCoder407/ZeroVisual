package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;

/** The smooth camera of OptiFine: the vanilla cinematic camera with an adjustable strength (MouseMixin). */
public class CinematicCamera extends Module {
	public final NumberSetting smoothness = add(new NumberSetting("Smoothness", 1, 0.3, 4, 0.1));

	public CinematicCamera() {
		super("CinematicCamera", "Smooth camera like in OptiFine, for filming", Category.VISUALS);
	}

	/** Time the smoothing sees: less of it means the camera follows the mouse more slowly. */
	public double smoothingTime(double time) {
		return isEnabled() ? time / smoothness.get() : time;
	}

	@Override
	public void onTick() {
		mc.options.smoothCamera = true;
	}

	@Override
	protected void onDisable() {
		mc.options.smoothCamera = false;
	}
}
