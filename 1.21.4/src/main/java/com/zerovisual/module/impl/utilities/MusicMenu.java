package com.zerovisual.module.impl.utilities;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.emote.EmoteSync;
import com.zerovisual.gui.MusicScreen;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.module.impl.cosmetics.EmoteMenu;
import com.zerovisual.module.impl.hud.Notifications;
import com.zerovisual.music.MusicPlayer;
import com.zerovisual.music.MusicQueue;
import com.zerovisual.music.MusicRoom;
import com.zerovisual.music.MusicTrack;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.KeySetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundCategory;
import org.lwjgl.glfw.GLFW;

/**
 * The music player: search Audius and Jamendo (through the relay), play full tracks, listen together with the
 * players on the same server. The work is in com.zerovisual.music; this module holds the settings and the key.
 */
public class MusicMenu extends Module {
	private static KeyBinding musicKey;

	public final KeySetting key = add(new KeySetting("Key", () -> musicKey));
	public final NumberSetting volume = add(new NumberSetting("Volume", 50, 0, 100, 1));
	public final BooleanSetting muted = add(new BooleanSetting("Mute", false));
	public final BooleanSetting share = add(new BooleanSetting("Listen together", true));
	public final BooleanSetting muteGameMusic = add(new BooleanSetting("Mute game music", true));
	public final BooleanSetting autoplay = add(new BooleanSetting("Autoplay next", true));
	/** What plays in Windows (Yandex Music, VK, Spotify, ...) shows and is controlled like a track of the player. */
	public final BooleanSetting systemPlayer = add(new BooleanSetting("System player", true));

	public MusicMenu() {
		super("MusicMenu", "Music player: search Audius and Jamendo, listen together with friends (M by default)", Category.UTILITIES);
		setEnabled(true);
		// -Dzerovisual.noBrowser: tests open the player page by hand
		if (!Boolean.getBoolean("zerovisual.noBrowser")) com.zerovisual.music.YouTubeLink.setOpener(url -> net.minecraft.util.Util.getOperatingSystem().open(url));
		MusicPlayer.setListener(new MusicPlayer.Listener() {
			@Override
			public void onFinished(MusicTrack track) {
				mc.execute(() -> finished());
			}

			@Override
			public void onFailed(MusicTrack track, String reason) {
				mc.execute(() -> failed(track, reason));
			}
		});
	}

	public static void registerKeyBinding() {
		musicKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.zerovisual.music", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.zerovisual"));
	}

	/** The music key, to close the screen with it too. */
	public static boolean isMusicKey(int keyCode, int scanCode) {
		return musicKey != null && musicKey.matchesKey(keyCode, scanCode);
	}

	/** Whether the relay room is needed for music (EmoteMenu connects when either wants it). */
	public boolean wantsRoom() {
		return isEnabled() && share.get();
	}

	@Override
	protected void onDisable() {
		com.zerovisual.music.SystemMedia.tick(false);
		MusicPlayer.stop();
		MusicQueue.clear();
		MusicRoom.unfollow();
		MusicRoom.tick(false);
	}

	@Override
	public void onTick() {
		MusicPlayer.setVolume(volume.getFloat() / 100f);
		MusicPlayer.setMaster(mc.options.getSoundVolume(SoundCategory.MASTER));
		MusicPlayer.setMuted(muted.get());
		com.zerovisual.music.YouTubeLink.tick();
		com.zerovisual.music.SystemMedia.tick(systemPlayer.get());
		// YouTube charts and search follow the country of the game language
		String language = mc.options.language;
		com.zerovisual.music.MusicCatalog.setRegion(language.length() >= 5 ? language.substring(3, 5) : "us");
		while (musicKey.wasPressed()) {
			if (mc.currentScreen == null) mc.setScreen(new MusicScreen(null));
		}
		boolean inWorld = mc.player != null;
		// with EmoteMenu off nobody else keeps the room open
		if (!ZeroVisualClient.get().modules().get(EmoteMenu.class).isEnabled()) {
			EmoteSync.setRoom(inWorld && share.get() ? EmoteMenu.place() : null, inWorld ? mc.player.getUuid() : null);
		}
		MusicRoom.tick(inWorld && share.get());
		if (muteGameMusic.get() && MusicPlayer.current() != null && !MusicPlayer.isPaused()) mc.getMusicTracker().stop();
	}

	private void finished() {
		// a followed player decides what comes next
		if (MusicRoom.following() != null) return;
		if (!autoplay.get() || !MusicQueue.next()) MusicPlayer.stop();
	}

	private void failed(MusicTrack track, String reason) {
		ZeroVisualClient.get().modules().get(Notifications.class).notify(
			I18n.translate("zerovisual.music.failed"), track.title, false);
		if (MusicRoom.following() != null || !MusicQueue.next()) MusicPlayer.stop();
	}
}
