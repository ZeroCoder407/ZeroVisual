package com.zerovisual.module.impl.utilities;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.ClickGuiScreen;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.ModeSetting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Opens the menu on its bind and holds the theme settings. */
public class ClickGui extends Module {
	public final ColorSetting accent = add(new ColorSetting("Accent", 0xFF8B5CF6));
	public final ModeSetting font = add(new ModeSetting("Font", "Inter", "Minecraft", "Inter", "Montserrat", "Rubik", "Nunito", "Comfortaa"));
	public final BooleanSetting blur = add(new BooleanSetting("Blur", true));

	public ClickGui() {
		super("ClickGUI", "Меню клиента и его оформление", Category.UTILITIES);
		setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	/** Font used by the menu and HUD; null means the vanilla font. */
	@Nullable
	public Identifier getFontId() {
		if (font.is("Minecraft")) return null;
		return Identifier.of(ZeroVisualClient.MOD_ID, font.get().toLowerCase(Locale.ROOT));
	}

	@Override
	public boolean isToggleable() {
		return false;
	}

	@Override
	public void onKeyPressed() {
		// Deferred: opening immediately would let the same key event reach the new screen and close it.
		mc.send(() -> {
			if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen(null));
		});
	}
}
