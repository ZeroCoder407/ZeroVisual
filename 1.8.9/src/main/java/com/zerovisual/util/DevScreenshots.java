package com.zerovisual.util;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.ClickGuiScreen;
import com.zerovisual.module.HudModule;
import com.zerovisual.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Development only: with the environment variable ZV_DEVSHOTS set, walks through the menu, a test world and the HUD
 * and saves screenshots to run/screenshots, then quits. Nobody presses anything.
 */
public final class DevScreenshots {
	private final Minecraft mc = Minecraft.getMinecraft();
	private int step, wait;
	private boolean titleShot;
	private String pendingShot;
	private DevModuleShots moduleShots;

	private DevScreenshots() {
	}

	public static void registerIfRequested() {
		if (System.getenv("ZV_DEVSHOTS") == null) return;
		MinecraftForge.EVENT_BUS.register(new DevScreenshots());
	}

	@SubscribeEvent
	public void onRender(TickEvent.RenderTickEvent event) {
		if (event.phase != TickEvent.Phase.END || pendingShot == null) return;
		ScreenShotHelper.saveScreenshot(mc.mcDataDir, pendingShot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
		ZeroVisualClient.LOGGER.info("Dev screenshot: {}", pendingShot);
		pendingShot = null;
	}

	private int musicStep;
	private int armStep;

	/** ZV_DEVSHOTS=music: the music player (needs a relay with /music: -Dzerovisual.relay=ws://127.0.0.1:8799). */
	private void music() {
		wait = 30;
		switch (musicStep++) {
			case 0:
				mc.displayGuiScreen(new com.zerovisual.gui.MusicScreen(null));
				break;
			case 3:
				shot("zv_music_popular");
				break;
			case 4:
				com.zerovisual.gui.MusicScreen.devPlay(0);
				break;
			case 5:
			case 6:
			case 7:
				ZeroVisualClient.LOGGER.info("[music] {} at {} ms, loading {}",
					com.zerovisual.music.MusicPlayer.current() == null ? "nothing" : com.zerovisual.music.MusicPlayer.current().title,
					com.zerovisual.music.MusicPlayer.position(), com.zerovisual.music.MusicPlayer.isLoading());
				break;
			case 8:
				shot("zv_music_playing");
				break;
			case 9:
				com.zerovisual.gui.MusicScreen.devSearch("макс корж");
				break;
			case 12:
				shot("zv_music_search");
				break;
			case 13:
				com.zerovisual.gui.MusicScreen.devTab(1);
				break;
			case 16:
				shot("zv_music_together");
				break;
			case 17:
				mc.displayGuiScreen(null);
				com.zerovisual.music.MusicPlayer.seek(60000);
				break;
			case 18:
			case 19:
			case 20:
				ZeroVisualClient.LOGGER.info("[music] after seek at {} ms, loading {}",
					com.zerovisual.music.MusicPlayer.position(), com.zerovisual.music.MusicPlayer.isLoading());
				break;
			case 21:
				shot("zv_music_hud");
				break;
			case 22:
				com.zerovisual.music.MusicPlayer.stop();
				break;
			case 23:
				mc.shutdown();
				break;
			default:
				break;
		}
	}

	private void shot(String name) {
		pendingShot = name;
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		if (wait > 0) {
			wait--;
			return;
		}
		if (step == 0 && !(mc.currentScreen instanceof GuiMainMenu)) return;
		if (step == 0 && !titleShot) {
			// the title screen as a fresh install shows it (a previous run may have changed the settings)
			com.zerovisual.module.impl.utilities.MainMenu menu = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.utilities.MainMenu.class);
			menu.background.set(true);
			menu.customLogo.set(true);
			menu.roundedButtons.set(true);
			shot("zv_title");
			titleShot = true;
			wait = 10;
			return;
		}
		switch (step) {
			case 0:
				mc.displayGuiScreen(new ClickGuiScreen(mc.currentScreen));
				wait = 30;
				break;
			case 1:
				shot("zv_menu_clickgui");
				wait = 10;
				break;
			case 2:
				mc.displayGuiScreen(null);
				mc.gameSettings.pauseOnLostFocus = false;
				WorldSettings settings = new WorldSettings(1234L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
				settings.enableCommands();
				mc.launchIntegratedServer("zvtest", "zvtest", settings);
				break;
			case 3:
				if (mc.thePlayer == null || mc.theWorld == null) return;
				for (Module m : ZeroVisualClient.get().modules().getModules()) {
					String n = m.getName();
					if (m instanceof HudModule || n.equals("Crosshair") || n.equals("BlockOutline") || n.equals("Saturation") || n.equals("TimeChanger")) m.setEnabled(true);
				}
				mc.thePlayer.sendChatMessage("/effect @p 1 300 1");
				mc.thePlayer.sendChatMessage("/effect @p 5 300 0");
				mc.thePlayer.sendChatMessage("/gamerule doDaylightCycle false");
				mc.thePlayer.sendChatMessage("Hello from ZeroVisual 1.8.9");
				mc.thePlayer.rotationPitch = 50;
				wait = 80;
				break;
			case 4:
				if ("arm".equals(System.getenv("ZV_DEVSHOTS"))) {
					// DEBUG-ARM: one emote, then quit
					if (armStep == 0) {
						for (Module m : ZeroVisualClient.get().modules().getModules()) {
							String n = m.getName();
							if (n.equals("CustomSky") || n.equals("Seasons") || n.equals("FireFlies") || n.equals("Trails") || n.equals("Halo") || n.equals("Wings") || n.equals("ChinaHat")) m.setEnabled(false);
						}
						mc.thePlayer.sendChatMessage("/time set 6000");
						mc.gameSettings.thirdPersonView = 2;
						mc.gameSettings.hideGUI = true;
						mc.thePlayer.rotationPitch = 15;
						com.zerovisual.emote.EmoteManager.play(mc.thePlayer.getUniqueID(), com.zerovisual.emote.Emotes.byId("wave"));
						wait = 40;
					} else if (armStep <= 8) {
						shot("zv_arm_" + armStep);
						wait = 2;
					} else {
						mc.shutdown();
					}
					armStep++;
					return;
				}
				if ("music".equals(System.getenv("ZV_DEVSHOTS"))) {
					music();
					return;
				}
				if ("modules".equals(System.getenv("ZV_DEVSHOTS"))) {
					if (moduleShots == null) moduleShots = new DevModuleShots();
					String name = moduleShots.tick();
					if (name == null) return;
					if (name.isEmpty()) {
						mc.shutdown();
						return;
					}
					shot(name);
					return;
				}
				shot("zv_hud");
				wait = 10;
				break;
			case 5:
				mc.displayGuiScreen(new ClickGuiScreen(null));
				wait = 30;
				break;
			case 6:
				shot("zv_world_clickgui");
				wait = 10;
				break;
			case 7:
				mc.displayGuiScreen(new GuiChat());
				wait = 20;
				break;
			case 8:
				shot("zv_hud_editor");
				wait = 10;
				break;
			case 9:
				mc.displayGuiScreen(null);
				mc.gameSettings.thirdPersonView = 2;
				mc.thePlayer.rotationPitch = -25;
				for (Module m : ZeroVisualClient.get().modules().getModules()) {
					String n = m.getName();
					if (n.equals("CustomSky") || n.equals("Nametags") || n.equals("Halo") || n.equals("Wings") || n.equals("ChinaHat") || n.equals("Trails") || n.equals("FireFlies") || n.equals("Seasons")) m.setEnabled(true);
					if (n.equals("TimeChanger")) m.setEnabled(false);
				}
				mc.thePlayer.sendChatMessage("/time set 6000");
				wait = 80;
				break;
			case 10:
				shot("zv_fx");
				wait = 10;
				break;
			case 11:
				for (Module m : ZeroVisualClient.get().modules().getModules()) {
					String n = m.getName();
					if (n.equals("Wings") || n.equals("Halo") || n.equals("ChinaHat") || n.equals("PlayerView") || n.equals("Minimap")) m.setEnabled(false);
				}
				mc.gameSettings.thirdPersonView = 2;
				mc.gameSettings.fovSetting = 40;
				mc.thePlayer.rotationPitch = 10;
				// through the wheel, as a player does: its blur must go away when it closes
				com.zerovisual.gui.EmoteWheelScreen.keepOpen = true;
				mc.displayGuiScreen(new com.zerovisual.gui.EmoteWheelScreen(ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.EmoteMenu.class)));
				wait = 10;
				break;
			case 12:
				com.zerovisual.gui.EmoteWheelScreen.keepOpen = false;
				mc.displayGuiScreen(null);
				com.zerovisual.emote.EmoteManager.play(mc.thePlayer.getUniqueID(), com.zerovisual.emote.Emotes.byId("rampage"));
				wait = 40;
				break;
			case 13:
				shot("zv_emote");
				wait = 25;
				break;
			case 14:
				shot("zv_emote2");
				com.zerovisual.emote.EmoteManager.stop(mc.thePlayer.getUniqueID());
				wait = 30;
				break;
			case 15:
				shot("zv_emote_after");
				wait = 10;
				break;
			case 16:
				mc.gameSettings.fovSetting = 70;
				mc.shutdown();
				break;
			default:
				return;
		}
		step++;
	}
}
