package com.zerovisual.module.impl.utilities;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * Holds the sprint key for you while moving forward, exactly like holding Ctrl.
 * Vanilla still decides whether you can sprint (hunger, using items, sneaking...).
 */
public class AutoSprint extends Module {
	public AutoSprint() {
		super("AutoSprint", "Автоматический бег при движении вперёд", Category.UTILITIES);
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		// with vanilla "Sprint: Toggle" the key is sticky: pressing it every tick would flip sprint on and off
		if (mc.options.getSprintToggled().getValue()) return;
		KeyBinding sprint = mc.options.sprintKey;
		sprint.setPressed(mc.options.forwardKey.isPressed() || physicallyHeld(sprint));
	}

	@Override
	protected void onDisable() {
		if (!mc.options.getSprintToggled().getValue()) {
			mc.options.sprintKey.setPressed(physicallyHeld(mc.options.sprintKey));
		}
	}

	private boolean physicallyHeld(KeyBinding binding) {
		InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
		return key.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(mc.getWindow().getHandle(), key.getCode());
	}
}
