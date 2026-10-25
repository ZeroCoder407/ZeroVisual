package com.zerovisual.module.impl.utilities;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;

/** Play With Friends: the buttons in the pause menu and the multiplayer screen (the work is in com.zerovisual.pwf). */
public class PlayWithFriends extends Module {
	/**
	 * Lets players without a Minecraft license join: the shared world skips the Mojang account check
	 * (like a server in offline mode). Anyone with the invite code may then pick any name.
	 */
	public final BooleanSetting noLicense = add(new BooleanSetting("Without license", false));

	public PlayWithFriends() {
		super("PlayWithFriends", "Friends join your world directly, no server or VPN", Category.UTILITIES);
		setEnabled(true);
	}
}
