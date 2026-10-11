package com.zerovisual.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.zerovisual.gui.ClickGuiScreen;

/** "Config" button in Mod Menu opens the ClickGUI. Loaded only when Mod Menu is installed. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ClickGuiScreen::new;
	}
}
