package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.emote.Emote;
import com.zerovisual.emote.EmoteManager;
import com.zerovisual.emote.EmoteSync;
import com.zerovisual.gui.EmoteWheelScreen;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.module.impl.hud.Notifications;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.KeySetting;
import com.zerovisual.util.Lang;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The emote wheel: hold the key, point at an emote, let go. Moving, jumping, attacking or using an item ends it.
 * Other players with ZeroVisual see the emote through {@link EmoteSync}.
 */
public class EmoteMenu extends Module {
	private static final int INVITE_RANGE = 24;
	private static KeyBinding emoteKey;

	public final KeySetting key = add(new KeySetting("Key", () -> emoteKey));
	public final BooleanSetting thirdPerson = add(new BooleanSetting("Third person", true));
	public final BooleanSetting sync = add(new BooleanSetting("Show to other players", true));

	/** The camera to go back to after an emote, -1 when the emote did not change it. */
	private int savedCamera = -1;
	private boolean wasPlaying;
	private final Set<UUID> invited = new HashSet<UUID>();

	public EmoteMenu() {
		super("EmoteMenu", "Emote wheel: dances and gestures (B by default), players with ZeroVisual see them", Category.COSMETICS);
		setEnabled(true);
	}

	public static void registerKeyBinding() {
		emoteKey = new KeyBinding("key.zerovisual.emotes", Keyboard.KEY_B, "category.zerovisual");
		ClientRegistry.registerKeyBinding(emoteKey);
	}

	/** Whether the key is physically held, also while the wheel (a screen) is open. */
	public static boolean isKeyHeld() {
		int code = emoteKey.getKeyCode();
		if (code < 0) return Mouse.isButtonDown(code + 100);
		return code > 0 && Keyboard.isKeyDown(code);
	}

	/** From the wheel. */
	public void play(Emote emote) {
		if (mc.thePlayer == null) return;
		EmoteManager.play(mc.thePlayer.getUniqueID(), emote);
		if (sync.get()) EmoteSync.sendPlay(emote.id());
	}

	/** Stops the own emote, for this client and everybody else. */
	public void stop() {
		if (mc.thePlayer == null) return;
		EmoteManager.stop(mc.thePlayer.getUniqueID());
		if (sync.get()) EmoteSync.sendStop();
	}

	@Override
	protected void onDisable() {
		stop();
		EmoteSync.setRoom(null, null);
	}

	@Override
	public void onTick() {
		if (mc.thePlayer == null) {
			EmoteSync.setRoom(null, null);
			EmoteManager.clear();
			savedCamera = -1;
			wasPlaying = false;
			return;
		}
		boolean music = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.utilities.MusicMenu.class).wantsRoom();
		EmoteSync.setRoom(sync.get() || music ? place() : null, mc.thePlayer.getUniqueID());
		if (mc.currentScreen == null && emoteKey.isPressed()) mc.displayGuiScreen(new EmoteWheelScreen(this));
		boolean playing = EmoteManager.current(mc.thePlayer.getUniqueID()) != null;
		if (playing && movementPressed()) {
			stop();
			playing = false;
		}
		if (playing && !wasPlaying && thirdPerson.get() && mc.gameSettings.thirdPersonView == 0) {
			savedCamera = 0;
			mc.gameSettings.thirdPersonView = 1;
		}
		if (!playing && wasPlaying) {
			// the player is free again: a finished emote tells the others too
			if (sync.get()) EmoteSync.sendStop();
			if (savedCamera >= 0) mc.gameSettings.thirdPersonView = savedCamera;
			savedCamera = -1;
		}
		wasPlaying = playing;
		inviteToGroups();
	}

	/** A card when somebody close by starts a group dance: choosing it in the wheel joins them. */
	private void inviteToGroups() {
		Emote mine = EmoteManager.current(mc.thePlayer.getUniqueID());
		for (EntityPlayer player : mc.theWorld.playerEntities) {
			UUID id = player.getUniqueID();
			Emote emote = EmoteManager.current(id);
			if (emote == null || !emote.group()) {
				invited.remove(id);
			} else if (player != mc.thePlayer && (mine == null || !mine.group())
				&& mc.thePlayer.getDistanceSqToEntity(player) < INVITE_RANGE * INVITE_RANGE && invited.add(id)) {
				ZeroVisualClient.get().modules().get(Notifications.class)
					.notify(Lang.get("zerovisual.emote.group"), Lang.get("zerovisual.emote.invite", player.getName()), true);
			}
		}
	}

	private boolean movementPressed() {
		GameSettings o = mc.gameSettings;
		return o.keyBindForward.isKeyDown() || o.keyBindBack.isKeyDown() || o.keyBindLeft.isKeyDown() || o.keyBindRight.isKeyDown()
			|| o.keyBindJump.isKeyDown() || o.keyBindAttack.isKeyDown() || o.keyBindUseItem.isKeyDown();
	}

	/** What identifies this game for everybody in it: the Play With Friends room, or the server address. */
	public static String place() {
		String room = com.zerovisual.pwf.PwfManager.room();
		if (room != null) return "pwf:" + room;
		// tests of emotes and music together in a singleplayer world
		String dev = System.getProperty("zerovisual.devRoom");
		if (dev != null) return "dev:" + dev;
		ServerData server = mc.getCurrentServerData();
		return server != null ? "server:" + server.serverIP.toLowerCase(Locale.ROOT) : null;
	}
}
