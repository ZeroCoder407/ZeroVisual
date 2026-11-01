package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.platform.InputConstants;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.KeySetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;

/**
 * While the freelook key is held, the mouse orbits the third-person camera around you
 * and your player keeps looking where it was. Camera only, see CameraMixin / EntityMixin.
 * Note: some servers do not allow freelook.
 */
public class Freelook extends Module {
	private static KeyMapping freelookKey;

	public final KeySetting key = add(new KeySetting("Key", () -> freelookKey));
	public final BooleanSetting frontView = add(new BooleanSetting("Front view", false));

	private boolean active;
	private float yaw, pitch;
	private CameraType previousPerspective;

	public Freelook() {
		super("Freelook", "Look around without turning your character (Alt by default). Not allowed on some servers", Category.VISUALS);
	}

	public static void registerKeyBinding() {
		freelookKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.zerovisual.freelook", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, com.zerovisual.ZeroVisualClient.KEY_CATEGORY));
	}

	@Override
	public void onTick() {
		boolean wanted = mc.player != null && freelookKey != null && freelookKey.isDown() && mc.screen == null;
		if (wanted && !active) start();
		else if (!wanted && active) stop();
	}

	@Override
	protected void onDisable() {
		if (active) stop();
	}

	private void start() {
		active = true;
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		previousPerspective = mc.options.getCameraType();
		mc.options.setCameraType(frontView.get() ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
	}

	private void stop() {
		active = false;
		if (previousPerspective != null) mc.options.setCameraType(previousPerspective);
	}

	public boolean isActive() {
		return active && isEnabled();
	}

	/** Same scaling as Entity#changeLookDirection. */
	public void rotate(double cursorDeltaX, double cursorDeltaY) {
		yaw += (float) cursorDeltaX * 0.15f;
		pitch = Mth.clamp(pitch + (float) cursorDeltaY * 0.15f, -90f, 90f);
	}

	public float getYaw() {
		return yaw;
	}

	public float getPitch() {
		return pitch;
	}
}
