package com.zerovisual.util;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.AccountScreen;
import com.zerovisual.gui.ClickGuiScreen;
import com.zerovisual.module.impl.cosmetics.HitParticles;
import com.zerovisual.module.impl.utilities.ClickGui;
import com.zerovisual.module.impl.visuals.NoHurtCam;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.List;

/**
 * Development helper, does nothing in normal play.
 * {@code --zvDevShot}: menu with every font, accounts and title screen.
 * {@code --zvDevShotWorld} (with quick play): HUD, cape in third person, hit particles, HUD editor.
 * Screenshots go to {@code screenshots/}, then the client quits.
 */
public final class DevScreenshots {
	private static final List<String> FONTS = List.of("Minecraft", "Inter", "Montserrat", "Rubik", "Nunito", "Comfortaa");
	private static int ticks;
	private static int step;
	private static boolean splashShot;
	private static int splashTicks;
	private static int movingTicks;
	private static int spinTicks;
	private static String pendingShot;
	private static int pendingShotTicks;

	private DevScreenshots() {
	}

	public static void registerIfRequested() {
		List<String> args = Arrays.asList(FabricLoader.getInstance().getLaunchArguments(true));
		if (args.contains("--zvDevShot")) {
			ZeroVisualClient.LOGGER.info("Dev screenshots: menus");
			ClientTickEvents.END_CLIENT_TICK.register(DevScreenshots::menus);
		} else if (args.contains("--zvDevShotWorld")) {
			ZeroVisualClient.LOGGER.info("Dev screenshots: world");
			ClientTickEvents.END_CLIENT_TICK.register(DevScreenshots::world);
		}
	}

	private static void menus(MinecraftClient mc) {
		// one shot of the loading screen while resources load
		if (!splashShot && mc.getOverlay() != null) {
			splashTicks++;
			if (splashTicks == 20) shot(mc, "zv_splash1");
			if (splashTicks == 50) {
				shot(mc, "zv_splash2");
				splashShot = true;
			}
			return;
		}
		if (step == 0 && !(mc.currentScreen instanceof TitleScreen)) return;
		ticks++;
		if (ticks % 40 != 0) return;

		ClickGui clickGui = ZeroVisualClient.get().modules().get(ClickGui.class);
		int fontStep = step / 2;
		if (fontStep < FONTS.size()) {
			if (step % 2 == 0) {
				clickGui.font.set(FONTS.get(fontStep));
				mc.setScreen(new ClickGuiScreen(null));
			} else {
				shot(mc, "zv_menu_" + FONTS.get(fontStep).toLowerCase());
			}
		} else if (step == FONTS.size() * 2) {
			clickGui.font.set("Inter");
			mc.setScreen(new AccountScreen(new TitleScreen()));
		} else if (step == FONTS.size() * 2 + 1) {
			shot(mc, "zv_accounts");
		} else if (step == FONTS.size() * 2 + 2) {
			mc.setScreen(new TitleScreen());
		} else if (step == FONTS.size() * 2 + 3) {
			shot(mc, "zv_title");
		} else {
			mc.scheduleStop();
		}
		step++;
	}

	private static void world(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) return;
		ticks++;
		if (movingTicks > 0) {
			// walk forward to draw a trail
			Vec3d look = mc.player.getRotationVector().multiply(1, 0, 1).normalize().multiply(0.25);
			mc.player.setVelocity(look.x, mc.player.getVelocity().y, look.z);
			movingTicks--;
		}
		if (spinTicks > 0) {
			mc.player.setYaw(mc.player.getYaw() + 9);
			spinTicks--;
		}
		if (pendingShot != null && --pendingShotTicks <= 0) {
			shot(mc, pendingShot);
			pendingShot = null;
		}
		if (ticks < 100 || ticks % 30 != 0) return;

		switch (step) {
			case 0 -> {
				ZeroVisualClient.get().modules().get(NoHurtCam.class).toggle(); // shows a notification
				var modules = ZeroVisualClient.get().modules();
				modules.get(com.zerovisual.module.impl.visuals.Nametags.class).setEnabled(true);
				modules.get(com.zerovisual.module.impl.visuals.Nametags.class).self.set(true);
				modules.get(com.zerovisual.module.impl.hud.Minimap.class).setEnabled(true);
				summonItems(mc);
				var waypoints = ZeroVisualClient.get().waypoints();
				if (waypoints.current().isEmpty()) {
					Vec3d ahead = mc.player.getPos().add(mc.player.getRotationVector().multiply(1, 0, 1).normalize().multiply(12));
					waypoints.add("База", (int) ahead.x, (int) ahead.y, (int) ahead.z);
					waypoints.add("Шахта", mc.player.getBlockX() + 400, 40, mc.player.getBlockZ() - 250);
				}
			}
			case 1 -> {
				// pretend the summoned pig was hit, for TargetESP / TargetHUD
				mc.world.getEntitiesByClass(PigEntity.class, mc.player.getBoundingBox().expand(8), e -> true)
					.stream().findFirst().ifPresent(TargetTracker::onAttack);
			}
			case 2 -> {
				// hurt the pig (DamageNumbers), screenshot while the number is visible
				runCommand(mc, "damage @e[type=pig,limit=1,sort=nearest] 3");
				pendingShot = "zv_world_hud";
				pendingShotTicks = 10;
			}
			case 3 -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				movingTicks = 25;
			}
			case 4 -> shot(mc, "zv_world_cape");
			case 5 -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
				HitParticles particles = ZeroVisualClient.get().modules().get(HitParticles.class);
				// long enough to survive until the next screenshot step
				particles.lifetime.set(3.0);
				particles.gravity.set(false);
				particles.spawn(mc.player);
			}
			case 6 -> {
				// kill the pig (KillEffect)
				runCommand(mc, "kill @e[type=pig]");
				pendingShot = "zv_world_particles";
				pendingShotTicks = 8;
			}
			case 7 -> {
				mc.options.setPerspective(Perspective.FIRST_PERSON);
				mc.setScreen(new ChatScreen(""));
			}
			case 8 -> shot(mc, "zv_world_chat");
			case 9 -> mc.setScreen(new com.zerovisual.gui.WaypointsScreen(null));
			case 10 -> shot(mc, "zv_world_waypoints");
			case 11 -> {
				mc.setScreen(null);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.MotionBlur.class).setEnabled(true);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.FullBright.class).setEnabled(true);
				spinTicks = 60;
			}
			case 12 -> shot(mc, "zv_world_motionblur");
			default -> mc.scheduleStop();
		}
		step++;
	}

	private static void runCommand(MinecraftClient mc, String command) {
		var server = mc.getServer();
		if (server == null) return;
		server.execute(() -> server.getCommandManager().executeWithPrefix(server.getCommandSource(), command));
	}

	/** Drops a few items in front of the player via the integrated server (to see ItemPhysics). */
	private static void summonItems(MinecraftClient mc) {
		var server = mc.getServer();
		if (server == null) return;
		Vec3d front = mc.player.getPos().add(mc.player.getRotationVector().multiply(1, 0, 1).normalize().multiply(2.5));
		String[] items = {"diamond_sword", "golden_apple", "grass_block", "ender_pearl"};
		String player = mc.player.getName().getString();
		server.execute(() -> {
			server.getCommandManager().executeWithPrefix(server.getCommandSource(), "give " + player + " minecraft:totem_of_undying 3");
			server.getCommandManager().executeWithPrefix(server.getCommandSource(), "give " + player + " minecraft:diamond_pickaxe 1");
			server.getCommandManager().executeWithPrefix(server.getCommandSource(), "item replace entity " + player + " armor.chest with minecraft:diamond_chestplate");
			server.getCommandManager().executeWithPrefix(server.getCommandSource(), "item replace entity " + player + " armor.head with minecraft:iron_helmet");
			// survival shows the hunger bar (Saturation)
			server.getCommandManager().executeWithPrefix(server.getCommandSource(), "gamemode survival " + player);
			server.getCommandManager().executeWithPrefix(server.getCommandSource(), String.format(java.util.Locale.ROOT,
				"summon pig %.2f %.2f %.2f {NoAI:1b}", front.x + 1.5, front.y + 0.5, front.z));
			for (int i = 0; i < items.length; i++) {
				String command = String.format(java.util.Locale.ROOT, "summon item %.2f %.2f %.2f {Item:{id:\"minecraft:%s\",count:1}}",
					front.x + (i - 1.5) * 0.6, front.y + 0.5, front.z, items[i]);
				server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
			}
		});
	}

	private static void shot(MinecraftClient mc, String name) {
		ScreenshotRecorder.saveScreenshot(mc.runDirectory, name + ".png", mc.getFramebuffer(),
			message -> ZeroVisualClient.LOGGER.info("Dev screenshot: {}", message.getString()));
	}
}
