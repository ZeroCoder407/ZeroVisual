package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;

/**
 * Raises the brightness above the vanilla gamma limit. 1.8.9 takes any gamma in its lightmap, so this sets it
 * while enabled and puts the player's own value back afterwards.
 */
public class FullBright extends Module {
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 10, 1, 15, 0.5));

	private float previousGamma = -1;

	public FullBright() {
		super("FullBright", "Unlimited brightness: see in the dark", Category.VISUALS);
	}

	@Override
	public void onTick() {
		if (previousGamma < 0) previousGamma = Math.min(1, mc.gameSettings.gammaSetting);
		mc.gameSettings.gammaSetting = brightness.getFloat();
	}

	@Override
	protected void onDisable() {
		if (previousGamma >= 0) mc.gameSettings.gammaSetting = previousGamma;
		previousGamma = -1;
	}
}
