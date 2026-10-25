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
	private static int startFrom;
	private static boolean splashShot;
	private static int splashTicks;
	private static int movingTicks;
	private static int spinTicks;
	/** Screenshots taken at a given tick, for effects that need several frames. */
	private static final java.util.Map<Integer, String> timedShots = new java.util.HashMap<>();
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
			// --zvDevShotFrom N: after the setup step, jump straight to step N
			int from = args.indexOf("--zvDevShotFrom");
			if (from >= 0 && from + 1 < args.size()) startFrom = Integer.parseInt(args.get(from + 1));
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
		if (timedShots.containsKey(ticks)) shot(mc, timedShots.remove(ticks));
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
				// ChatHeads: a short message and one long enough to wrap
				mc.player.networkHandler.sendChatMessage("Привет! Это ChatHeads");
				mc.player.networkHandler.sendChatMessage("Длинное сообщение, чтобы проверить перенос строк: голова только у первой строки, а текст всех строк выровнен");
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
			case 13 -> {
				// an open platform high above the terrain, so walls do not hide the effect
				String name = mc.player.getName().getString();
				runCommand(mc, "execute at " + name + " run fill ~-5 ~30 ~-5 ~5 ~30 ~5 minecraft:smooth_stone");
				runCommand(mc, "execute at " + name + " run tp " + name + " ~ ~31 ~");
			}
			case 14 -> {
				// JumpCircles star modes, looking down at the feet
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.MotionBlur.class).setEnabled(false);
				spinTicks = 0;
				mc.player.setPitch(75);
				var jump = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.JumpCircles.class);
				jump.setEnabled(true);
				jump.mode.set("Star");
				jump.spawn(mc.player.getPos());
				pendingShot = "zv_world_star";
				pendingShotTicks = 5;
			}
			case 15 -> {
				var jump = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.JumpCircles.class);
				jump.mode.set("Shatter");
				jump.breakAfter.set(0.3);
				jump.spawn(mc.player.getPos());
				// 0.25 s appear + 0.3 s whole, then a quarter of the shatter
				pendingShot = "zv_world_shatter";
				pendingShotTicks = 14;
			}
			case 16 -> {
				// KillEffect black hole: a pig on the platform in front of the camera
				mc.player.setPitch(10);
				Vec3d front = mc.player.getPos().add(mc.player.getRotationVector().multiply(1, 0, 1).normalize().multiply(4));
				runCommand(mc, String.format(java.util.Locale.ROOT, "summon pig %.2f %.2f %.2f {NoAI:1b}", front.x, front.y, front.z));
				var kill = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.KillEffect.class);
				kill.setEnabled(true);
				kill.style.set("Black hole");
			}
			case 17 -> {
				mc.world.getEntitiesByClass(PigEntity.class, mc.player.getBoundingBox().expand(8), e -> true)
					.stream().findFirst().ifPresent(TargetTracker::onAttack);
				runCommand(mc, "kill @e[type=pig]");
				// descending, pulling the body in, collapsing with the shockwave
				timedShots.put(ticks + 18, "zv_world_hole1");
				timedShots.put(ticks + 40, "zv_world_hole2");
				timedShots.put(ticks + 60, "zv_world_hole3");
			}
			case 18, 19 -> {
				// wait for the timed screenshots
			}
			case 20 -> {
				// CustomSky: galaxy at night, looking up
				var sky = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.CustomSky.class);
				sky.setEnabled(true);
				sky.mode.set("Galaxy");
				runCommand(mc, "time set 18000");
				mc.player.setYaw(0);
				mc.player.setPitch(-35);
			}
			case 21 -> shot(mc, "zv_sky_galaxy");
			case 22 -> {
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.CustomSky.class).mode.set("Realistic");
				runCommand(mc, "time set 12400");
				mc.player.setYaw(90);
				mc.player.setPitch(-10);
			}
			case 23 -> shot(mc, "zv_sky_sunset");
			case 24 -> {
				var sky = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.CustomSky.class);
				sky.setEnabled(true);
				sky.mode.set("Black hole");
				// clean shot for banners: no HUD, no hand, high above the trees, nothing else in view
				mc.options.hudHidden = true;
				var m = ZeroVisualClient.get().modules();
				m.get(com.zerovisual.module.impl.utilities.Waypoints.class).setEnabled(false);
				m.get(com.zerovisual.module.impl.cosmetics.Pet.class).setEnabled(false);
				String who = mc.player.getName().getString();
				runCommand(mc, "effect clear " + who);
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "execute at " + who + " run fill ~-2 140 ~-2 ~2 140 ~2 minecraft:barrier");
				runCommand(mc, "execute at " + who + " run tp " + who + " ~ 141 ~");
				mc.player.setYaw(-143.7f);
				mc.player.setPitch(-12);
			}
			case 25 -> {
				shot(mc, "zv_sky_blackhole");
				mc.options.hudHidden = false;
			}
			case 26 -> {
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.CustomSky.class).setEnabled(false);
				runCommand(mc, "time set 6000");
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Pet.class).setEnabled(true);
				var glint = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.GlintColor.class);
				glint.setEnabled(true);
				glint.color.set(0xFFFF3050);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.hud.LowHealth.class).setEnabled(true);
				String name = mc.player.getName().getString();
				runCommand(mc, "item replace entity " + name + " weapon.mainhand with minecraft:iron_sword[enchantments={levels:{\"minecraft:sharpness\":5}}]");
				runCommand(mc, "item replace entity " + name + " weapon.offhand with minecraft:totem_of_undying");
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				mc.player.setYaw(30);
				mc.player.setPitch(15);
				// walk so the pet runs after the player
				movingTicks = 40;
			}
			case 27 -> shot(mc, "zv_pet");
			case 28 -> mc.options.setPerspective(Perspective.FIRST_PERSON);
			case 29 -> shot(mc, "zv_glint");
			case 30 -> {
				runCommand(mc, "damage " + mc.player.getName().getString() + " 100");
				timedShots.put(ticks + 7, "zv_totem1");
				timedShots.put(ticks + 22, "zv_totem2");
			}
			case 31, 32 -> {
				// wait for the timed screenshots
			}
			case 33 -> {
				// Pet (Mini me) close-up without the HUD, on open ground
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "execute at " + who + " run fill ~-4 150 ~-4 ~4 150 ~4 minecraft:barrier");
				runCommand(mc, "execute at " + who + " run tp " + who + " ~ 151 ~");
				mc.options.hudHidden = true;
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				mc.player.setPitch(25);
				var pet = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Pet.class);
				pet.setEnabled(true);
				pet.type.set("Mini me");
				pet.armor.set("Diamond");
			}
			case 34 -> {
				// wait for the pet to appear next to the player
			}
			case 35 -> shot(mc, "zv_pet_stand");
			case 36 -> {
				// turn around: the pet runs to the player
				mc.player.setYaw(mc.player.getYaw() + 150);
				mc.player.setBodyYaw(mc.player.getYaw());
				timedShots.put(ticks + 6, "zv_pet_run");
			}
			case 37, 38, 39 -> {
				// stand still so Mini me sits down
			}
			case 40 -> shot(mc, "zv_pet_sit");
			case 41 -> {
				// ItemPhysics: throw the held stack and catch it in the air
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.ItemPhysics.class).setEnabled(true);
				mc.options.hudHidden = true;
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				mc.player.setPitch(-5);
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "item replace entity " + who + " weapon.mainhand with minecraft:golden_apple 16");
			}
			case 42 -> {
				mc.player.dropSelectedItem(true);
				timedShots.put(ticks + 4, "zv_items_fly");
				timedShots.put(ticks + 8, "zv_items_fly2");
			}
			case 43 -> mc.options.hudHidden = false;
			case 44 -> {
				// Mini me close-up for banners: on a platform in the sky, right in front of the camera
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "execute at " + who + " run fill ~-4 150 ~-4 ~4 150 ~4 minecraft:barrier");
				runCommand(mc, "execute at " + who + " run tp " + who + " ~ 151 ~ 0 40");
				mc.options.hudHidden = true;
				mc.options.setPerspective(Perspective.FIRST_PERSON);
				var pet = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Pet.class);
				pet.setEnabled(true);
				pet.type.set("Mini me");
				pet.armor.set("Diamond");
			}
			case 45 -> {
				mc.player.setYaw(0);
				mc.player.setPitch(40);
				var pet = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Pet.class);
				// 2 blocks south (in front, yaw 0), facing back north at the player
				pet.placeAt(mc.player.getPos().add(0, 0, 2), 180);
				timedShots.put(ticks + 70, "zv_mini_arms");
				timedShots.put(ticks + 210, "zv_mini_tap");
				timedShots.put(ticks + 330, "zv_mini_sit");
			}
			case 46, 47, 48, 49, 50, 51, 52, 53, 54, 55 -> {
				// wait for the idle animations
			}
			case 56 -> mc.options.hudHidden = false;
			case 57 -> {
				// HitboxColor: a pig right under the crosshair
				mc.options.hudHidden = false;
				mc.options.setPerspective(Perspective.FIRST_PERSON);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.HitboxColor.class).setEnabled(true);
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				mc.player.setPitch(35);
				// exactly on the line of sight, 2.5 blocks ahead (the pig is 0.9 high)
				var spot = mc.player.getEyePos().add(mc.player.getRotationVec(1f).multiply(2.5, 2.5, 2.5)).add(0, -0.45, 0);
				runCommand(mc, String.format(java.util.Locale.ROOT, "summon minecraft:pig %.2f %.2f %.2f {NoAI:1b}", spot.x, spot.y, spot.z));
				timedShots.put(ticks + 15, "zv_hitbox");
			}
			case 58 -> {
				// SmoothSwap and ItemZoom: shift click a stack in the inventory (slow flight so a screenshot catches it)
				var modules = ZeroVisualClient.get().modules();
				modules.get(com.zerovisual.module.impl.visuals.SmoothSwap.class).setEnabled(true);
				modules.get(com.zerovisual.module.impl.visuals.SmoothSwap.class).duration.set(400.0);
				modules.get(com.zerovisual.module.impl.visuals.ItemZoom.class).setEnabled(true);
				runCommand(mc, "item replace entity " + mc.player.getName().getString() + " hotbar.0 with minecraft:diamond 32");
				mc.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(mc.player));
			}
			case 59 -> {
				mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 36, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
				timedShots.put(ticks + 3, "zv_swap1");
				timedShots.put(ticks + 5, "zv_swap2");
			}
			case 60 -> {
				shot(mc, "zv_swap_done");
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.SmoothSwap.class).duration.set(160.0);
				mc.setScreen(null);
			}
			case 61 -> {
				// Play With Friends: open the world to friends, a pretend friend joins through the tunnel and pings the world
				mc.options.hudHidden = false;
				mc.setScreen(com.zerovisual.gui.PwfScreen.host(null));
			}
			case 62 -> {
				// the Without license option, as the user would switch it on
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.utilities.PlayWithFriends.class).noLicense.set(true);
				mc.getServer().setOnlineMode(false);
				com.zerovisual.pwf.PwfSelfTest.run(com.zerovisual.pwf.PwfManager.currentHost(mc.getServer().getServerPort()), mc.getServer().getServerPort(), ZeroVisualClient.LOGGER::info);
				timedShots.put(ticks + 4, "zv_pwf_invite");
			}
			case 63, 64 -> {
				// waiting for the connection
			}
			case 65 -> shot(mc, "zv_pwf_connected");
			case 66 -> {
				// ShieldColor: own shield in the off hand, a husk with a shield in front (it does not burn in the sun)
				mc.setScreen(null);
				mc.options.setPerspective(Perspective.FIRST_PERSON);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.ShieldColor.class).setEnabled(true);
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "item replace entity " + who + " weapon.offhand with minecraft:shield");
				mc.player.setPitch(10);
				Vec3d spot = mc.player.getPos().add(mc.player.getRotationVector().multiply(1, 0, 1).normalize().multiply(2.5));
				runCommand(mc, String.format(java.util.Locale.ROOT, "summon minecraft:husk %.2f %.2f %.2f {NoAI:1b,Silent:1b,HandItems:[{id:\"minecraft:shield\",count:1},{}],Rotation:[%.1ff,0f]}",
					spot.x, spot.y, spot.z, mc.player.getYaw() + 180));
				timedShots.put(ticks + 230, "zv_shield_ready");
			}
			case 67, 68, 69, 70, 71, 72, 73, 74 -> {
				// the chat fades out
			}
			case 75 -> {
				// an axe hit: own shield on cooldown, the husk's marked as broken
				mc.player.getItemCooldownManager().set(mc.player.getOffHandStack(), 100);
				var shield = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.ShieldColor.class);
				mc.world.getEntitiesByClass(net.minecraft.entity.mob.HuskEntity.class, mc.player.getBoundingBox().expand(8), e -> true).forEach(shield::onShieldBreak);
				timedShots.put(ticks + 5, "zv_shield_broken");
			}
			case 76 -> {
				mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
				timedShots.put(ticks + 10, "zv_shield_f5");
			}
			case 77, 78 -> {
				// the 5 second cooldown runs out
			}
			case 79 -> {
				mc.options.setPerspective(Perspective.FIRST_PERSON);
				timedShots.put(ticks + 5, "zv_shield_after");
			}
			case 80 -> {
			}
			default -> mc.scheduleStop();
		}
		step++;
		if (step == 1 && startFrom > 1) step = startFrom;
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
