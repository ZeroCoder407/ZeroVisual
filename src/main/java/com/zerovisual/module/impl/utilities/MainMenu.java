package com.zerovisual.module.impl.utilities;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;

/** Settings for the title screen: falling-stars background and the account button. */
public class MainMenu extends Module {
	public final BooleanSetting background = add(new BooleanSetting("Star background", true));
	public final NumberSetting stars = add(new NumberSetting("Stars", 70, 10, 200, 5)
		.visibleWhen(background::get));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1, 0.2, 3, 0.1)
		.visibleWhen(background::get));
	public final BooleanSetting accountButton = add(new BooleanSetting("Account button", true));
	public final BooleanSetting customLogo = add(new BooleanSetting("ZeroVisual logo", true));
	public final BooleanSetting roundedButtons = add(new BooleanSetting("Rounded buttons", true));

	public MainMenu() {
		super("MainMenu", "Main menu background and account button", Category.UTILITIES);
	}

	@Override
	public boolean isToggleable() {
		return false;
	}

	@Override
	public void onKeyPressed() {
	}
}
