package com.zerovisual.module.impl.cosmetics;

import com.mojang.blaze3d.platform.InputConstants;
import com.zerovisual.emote.Emote;
import com.zerovisual.emote.EmoteManager;
import com.zerovisual.emote.EmoteSync;
import com.zerovisual.gui.EmoteWheelScreen;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.pwf.PwfManager;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.KeySetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ServerData;

/**
 * The emote wheel: hold the key, point at an emote, let go. Moving, jumping, attacking or using an item ends it.
 * Other players with ZeroVisual see the emote through {@link EmoteSync}.
 */
public class EmoteMenu extends Module {
	private static KeyMapping emoteKey;

	public final KeySetting key = add(new KeySetting("Key", () -> emoteKey));
	public final BooleanSetting thirdPerson = add(new BooleanSetting("Third person", true));
	public final BooleanSetting sync = add(new BooleanSetting("Show to other players", true));

	private static final int INVITE_RANGE = 24;

	/** The camera to go back to after an emote, null when the emote did not change it. */
	private CameraType savedCamera;
	private boolean wasPlaying;
	private final java.util.Set<java.util.UUID> invited = new java.util.HashSet<>();

	public EmoteMenu() {
		super("EmoteMenu", "Emote wheel: dances and gestures (B by default), players with ZeroVisual see them", Category.COSMETICS);
		setEnabled(true);
	}

	public static void registerKeyBinding() {
		emoteKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.zerovisual.emotes", InputConstants.Type.KEYSYM, InputConstants.KEY_B, com.zerovisual.ZeroVisualClient.KEY_CATEGORY));
	}

	public static boolean isKeyHeld() {
		InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(emoteKey);
		return bound.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), bound.getValue());
	}

	/** From the wheel. */
	public void play(Emote emote) {
		if (mc.player == null) return;
		EmoteManager.play(mc.player.getUUID(), emote);
		if (sync.get()) EmoteSync.sendPlay(emote.id());
	}

	/** Stops the own emote, for this client and everybody else. */
	public void stop() {
		if (mc.player == null) return;
		EmoteManager.stop(mc.player.getUUID());
		if (sync.get()) EmoteSync.sendStop();
	}

	@Override
	protected void onDisable() {
		stop();
		EmoteSync.setRoom(null, null);
	}

	@Override
	public void onTick() {
		if (mc.player == null) {
			EmoteSync.setRoom(null, null);
			EmoteManager.clear();
			savedCamera = null;
			wasPlaying = false;
			return;
		}
		EmoteSync.setRoom(sync.get() ? place() : null, mc.player.getUUID());
		if (mc.screen == null && emoteKey.isDown()) mc.setScreen(new EmoteWheelScreen(this));

		boolean playing = EmoteManager.current(mc.player.getUUID()) != null;
		if (playing && movementPressed()) {
			stop();
			playing = false;
		}
		if (playing && !wasPlaying && thirdPerson.get() && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
			savedCamera = CameraType.FIRST_PERSON;
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		if (!playing && wasPlaying) {
			// the player is free again: a finished emote tells the others too
			if (sync.get()) EmoteSync.sendStop();
			if (savedCamera != null) mc.options.setCameraType(savedCamera);
			savedCamera = null;
		}
		wasPlaying = playing;
		inviteToGroups();
	}

	/** A card when somebody close by starts a group dance: choosing it in the wheel joins them. */
	private void inviteToGroups() {
		Emote mine = EmoteManager.current(mc.player.getUUID());
		for (var player : mc.level.players()) {
			java.util.UUID id = player.getUUID();
			Emote emote = EmoteManager.current(id);
			if (emote == null || !emote.group()) invited.remove(id);
			else if (player != mc.player && (mine == null || !mine.group()) && mc.player.distanceToSqr(player) < INVITE_RANGE * INVITE_RANGE && invited.add(id)) {
				com.zerovisual.ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.hud.Notifications.class)
					.notify(net.minecraft.client.resources.language.I18n.get("zerovisual.emote.group"), net.minecraft.client.resources.language.I18n.get("zerovisual.emote.invite", player.getName().getString()), true);
			}
		}
	}

	private boolean movementPressed() {
		var o = mc.options;
		return o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown() || o.keyJump.isDown()
			|| o.keyAttack.isDown() || o.keyUse.isDown();
	}

	/** What identifies this game for everybody in it: the Play With Friends room, or the server address. */
	private String place() {
		String room = PwfManager.room();
		if (room != null) return "pwf:" + room;
		ServerData server = mc.getCurrentServer();
		return server != null ? "server:" + server.ip.toLowerCase(java.util.Locale.ROOT) : null;
	}
}
