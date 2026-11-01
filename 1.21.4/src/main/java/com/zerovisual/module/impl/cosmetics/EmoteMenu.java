package com.zerovisual.module.impl.cosmetics;

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
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * The emote wheel: hold the key, point at an emote, let go. Moving, jumping, attacking or using an item ends it.
 * Other players with ZeroVisual see the emote through {@link EmoteSync}.
 */
public class EmoteMenu extends Module {
	private static KeyBinding emoteKey;

	public final KeySetting key = add(new KeySetting("Key", () -> emoteKey));
	public final BooleanSetting thirdPerson = add(new BooleanSetting("Third person", true));
	public final BooleanSetting sync = add(new BooleanSetting("Show to other players", true));

	private static final int INVITE_RANGE = 24;

	/** The camera to go back to after an emote, null when the emote did not change it. */
	private Perspective savedCamera;
	private boolean wasPlaying;
	private final java.util.Set<java.util.UUID> invited = new java.util.HashSet<>();

	public EmoteMenu() {
		super("EmoteMenu", "Emote wheel: dances and gestures (B by default), players with ZeroVisual see them", Category.COSMETICS);
		setEnabled(true);
	}

	public static void registerKeyBinding() {
		emoteKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.zerovisual.emotes", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.zerovisual"));
	}

	public static boolean isKeyHeld() {
		InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(emoteKey);
		return bound.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(mc.getWindow().getHandle(), bound.getCode());
	}

	/** From the wheel. */
	public void play(Emote emote) {
		if (mc.player == null) return;
		EmoteManager.play(mc.player.getUuid(), emote);
		if (sync.get()) EmoteSync.sendPlay(emote.id());
	}

	/** Stops the own emote, for this client and everybody else. */
	public void stop() {
		if (mc.player == null) return;
		EmoteManager.stop(mc.player.getUuid());
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
		EmoteSync.setRoom(sync.get() ? place() : null, mc.player.getUuid());
		if (mc.currentScreen == null && emoteKey.isPressed()) mc.setScreen(new EmoteWheelScreen(this));

		boolean playing = EmoteManager.current(mc.player.getUuid()) != null;
		if (playing && movementPressed()) {
			stop();
			playing = false;
		}
		if (playing && !wasPlaying && thirdPerson.get() && mc.options.getPerspective() == Perspective.FIRST_PERSON) {
			savedCamera = Perspective.FIRST_PERSON;
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
		}
		if (!playing && wasPlaying) {
			// the player is free again: a finished emote tells the others too
			if (sync.get()) EmoteSync.sendStop();
			if (savedCamera != null) mc.options.setPerspective(savedCamera);
			savedCamera = null;
		}
		wasPlaying = playing;
		inviteToGroups();
	}

	/** A card when somebody close by starts a group dance: choosing it in the wheel joins them. */
	private void inviteToGroups() {
		Emote mine = EmoteManager.current(mc.player.getUuid());
		for (var player : mc.world.getPlayers()) {
			java.util.UUID id = player.getUuid();
			Emote emote = EmoteManager.current(id);
			if (emote == null || !emote.group()) invited.remove(id);
			else if (player != mc.player && (mine == null || !mine.group()) && mc.player.squaredDistanceTo(player) < INVITE_RANGE * INVITE_RANGE && invited.add(id)) {
				com.zerovisual.ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.hud.Notifications.class)
					.notify(net.minecraft.client.resource.language.I18n.translate("zerovisual.emote.group"), net.minecraft.client.resource.language.I18n.translate("zerovisual.emote.invite", player.getName().getString()), true);
			}
		}
	}

	private boolean movementPressed() {
		var o = mc.options;
		return o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed() || o.jumpKey.isPressed()
			|| o.attackKey.isPressed() || o.useKey.isPressed();
	}

	/** What identifies this game for everybody in it: the Play With Friends room, or the server address. */
	private String place() {
		String room = PwfManager.room();
		if (room != null) return "pwf:" + room;
		ServerInfo server = mc.getCurrentServerEntry();
		return server != null ? "server:" + server.address.toLowerCase(java.util.Locale.ROOT) : null;
	}
}
