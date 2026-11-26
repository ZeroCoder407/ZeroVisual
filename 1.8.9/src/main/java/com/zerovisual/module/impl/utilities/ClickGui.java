package com.zerovisual.module.impl.utilities;

import com.zerovisual.gui.ClickGuiScreen;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.ModeSetting;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

/** Opens the menu on its bind and holds the theme settings. */
public class ClickGui extends Module {
	public final ColorSetting accent = add(new ColorSetting("Accent", 0xFF8B5CF6));
	public final ModeSetting font = add(new ModeSetting("Font", "Inter", "Minecraft", "Inter", "Montserrat", "Rubik", "Nunito", "Comfortaa"));
	public final BooleanSetting blur = add(new BooleanSetting("Blur", true));

	public ClickGui() {
		super("ClickGUI", "Client menu and its look", Category.UTILITIES);
		setKey(Keyboard.KEY_RSHIFT);
	}

	/** File name of the font used by the menu and HUD; null means the vanilla font. */
	public String getFontName() {
		if (font.is("Minecraft")) return null;
		return font.get().toLowerCase(Locale.ROOT);
	}

	@Override
	public boolean isToggleable() {
		return false;
	}

	@Override
	public void onKeyPressed() {
		// deferred: opening immediately would let the same key event reach the new screen and close it
		mc.addScheduledTask(() -> {
			if (mc.currentScreen == null) mc.displayGuiScreen(new ClickGuiScreen(null));
		});
	}
}
