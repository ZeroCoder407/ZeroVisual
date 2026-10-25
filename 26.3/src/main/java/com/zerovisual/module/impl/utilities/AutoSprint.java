package com.zerovisual.module.impl.utilities;

import com.mojang.blaze3d.platform.InputConstants;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

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
		if (mc.player == null) return;
		// with vanilla "Sprint: Toggle" the key is sticky: pressing it every tick would flip sprint on and off
		if (mc.options.toggleSprint().get()) return;
		KeyMapping sprint = mc.options.keySprint;
		sprint.setDown(mc.options.keyUp.isDown() || physicallyHeld(sprint));
	}

	@Override
	protected void onDisable() {
		if (!mc.options.toggleSprint().get()) {
			mc.options.keySprint.setDown(physicallyHeld(mc.options.keySprint));
		}
	}

	private boolean physicallyHeld(KeyMapping binding) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(binding);
		return key.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(key.getValue());
	}
}
