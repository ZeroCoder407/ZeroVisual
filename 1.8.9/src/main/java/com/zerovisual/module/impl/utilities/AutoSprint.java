package com.zerovisual.module.impl.utilities;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Holds the sprint key for you while moving forward, exactly like holding Ctrl.
 * Vanilla still decides whether you can sprint (hunger, using items, sneaking...).
 */
public class AutoSprint extends Module {
	public AutoSprint() {
		super("AutoSprint", "Sprint automatically while moving forward", Category.UTILITIES);
	}

	@Override
	public void onTick() {
		if (mc.thePlayer == null) return;
		KeyBinding sprint = mc.gameSettings.keyBindSprint;
		KeyBinding.setKeyBindState(sprint.getKeyCode(), mc.gameSettings.keyBindForward.isKeyDown() || physicallyHeld(sprint));
	}

	@Override
	protected void onDisable() {
		KeyBinding sprint = mc.gameSettings.keyBindSprint;
		KeyBinding.setKeyBindState(sprint.getKeyCode(), physicallyHeld(sprint));
	}

	private static boolean physicallyHeld(KeyBinding binding) {
		int code = binding.getKeyCode();
		if (code < 0) return Mouse.isButtonDown(code + 100);
		return code > 0 && Keyboard.isKeyDown(code);
	}
}
