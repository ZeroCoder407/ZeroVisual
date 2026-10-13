package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.Anim;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.KeySetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** OptiFine-style zoom while the zoom key is held. FOV is changed in GameRendererMixin. */
public class Zoom extends Module {
	private static KeyBinding zoomKey;

	public final KeySetting key = add(new KeySetting("Key", () -> zoomKey));
	public final NumberSetting factor = add(new NumberSetting("Factor", 4, 1.5, 10, 0.5));
	public final BooleanSetting smooth = add(new BooleanSetting("Smooth", true));
	public final BooleanSetting scroll = add(new BooleanSetting("Scroll", true));

	private final Anim anim = new Anim(14f, 1f);
	/** Factor changed with the mouse wheel during the current zoom, or -1. */
	private float scrolled = -1;

	public Zoom() {
		super("Zoom", "Zoom the camera while the key is held (C by default), mouse wheel changes the zoom", Category.VISUALS);
		setEnabled(true);
	}

	public static void registerKeyBinding() {
		zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.zerovisual.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.zerovisual"));
	}

	public boolean isZooming() {
		return isEnabled() && zoomKey != null && zoomKey.isPressed() && mc.currentScreen == null;
	}

	/** Returns the FOV divisor for the current frame (1 = no zoom). */
	public float getDivisor() {
		boolean zooming = isZooming();
		if (!zooming) scrolled = -1;
		float target = zooming ? (scrolled > 0 ? scrolled : factor.getFloat()) : 1f;
		if (!smooth.get()) {
			anim.set(target);
			return target;
		}
		return anim.update(target);
	}

	/** Mouse wheel while zooming; returns true when the scroll was used (the hotbar must not change). */
	public boolean onScroll(double amount) {
		if (!scroll.get() || !isZooming() || amount == 0) return false;
		float current = scrolled > 0 ? scrolled : factor.getFloat();
		scrolled = Math.max(1.5f, Math.min(50f, current * (amount > 0 ? 1.25f : 0.8f)));
		return true;
	}
}
