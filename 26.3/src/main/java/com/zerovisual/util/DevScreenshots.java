package com.zerovisual.util;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.AccountScreen;
import com.zerovisual.gui.ClickGuiScreen;
import com.zerovisual.module.impl.cosmetics.HitParticles;
import com.zerovisual.module.impl.utilities.ClickGui;
import com.zerovisual.module.impl.visuals.NoHurtCam;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;
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

	private static void menus(Minecraft mc) {
		// one shot of the loading screen while resources load
		if (!splashShot && mc.gui.overlay() != null) {
			splashTicks++;
			if (splashTicks == 20) shot(mc, "zv_splash1");
			if (splashTicks == 50) {
				shot(mc, "zv_splash2");
				splashShot = true;
			}
			return;
		}
		if (step == 0 && !(mc.gui.screen() instanceof TitleScreen)) return;
		ticks++;
		if (ticks % 40 != 0) return;

		ClickGui clickGui = ZeroVisualClient.get().modules().get(ClickGui.class);
		int fontStep = step / 2;
		if (fontStep < FONTS.size()) {
			if (step % 2 == 0) {
				clickGui.font.set(FONTS.get(fontStep));
				mc.gui.setScreen(new ClickGuiScreen(null));
			} else {
				shot(mc, "zv_menu_" + FONTS.get(fontStep).toLowerCase());
			}
		} else if (step == FONTS.size() * 2) {
			clickGui.font.set("Inter");
			mc.gui.setScreen(new AccountScreen(new TitleScreen()));
		} else if (step == FONTS.size() * 2 + 1) {
			shot(mc, "zv_accounts");
		} else if (step == FONTS.size() * 2 + 2) {
			mc.gui.setScreen(new TitleScreen());
		} else if (step == FONTS.size() * 2 + 3) {
			shot(mc, "zv_title");
		} else {
			mc.stop();
		}
		step++;
	}

	// each season gets 15 seconds, so that a carpet has time to settle
	private static final int SEASONS_STEP = 218;

	private static void seasons(Minecraft mc) {
		String[] modes = {"Autumn", "Winter", "Spring", "Summer"};
		int n = step - SEASONS_STEP, season = n / 10;
		var module = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.Seasons.class);
		if (season >= modes.length) {
			module.setEnabled(false);
			mc.stop();
			return;
		}
		if (n % 10 == 0) {
			hideGui(mc, true);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			mc.player.setXRot(18);
			runCommand(mc, "time set noon");
			module.mode.set(modes[season]);
			module.setEnabled(true);
		}
		if (n % 10 == 9) timedShots.put(ticks + 5, "zv_seasons_" + modes[season].toLowerCase());
		step++;
	}

	private static void world(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		ticks++;
		if (movingTicks > 0) {
			// walk forward to draw a trail
			Vec3 look = mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.25);
			mc.player.setDeltaMovement(look.x, mc.player.getDeltaMovement().y, look.z);
			movingTicks--;
		}
		if (spinTicks > 0) {
			mc.player.setYRot(mc.player.getYRot() + 9);
			spinTicks--;
		}
		if (pendingShot != null && --pendingShotTicks <= 0) {
			shot(mc, pendingShot);
			pendingShot = null;
		}
		if (timedShots.containsKey(ticks)) shot(mc, timedShots.remove(ticks));
		if (ticks < 100 || ticks % 30 != 0) return;
		if (step >= SEASONS_STEP) {
			seasons(mc);
			return;
		}

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
					Vec3 ahead = mc.player.position().add(mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(12));
					waypoints.add("База", (int) ahead.x, (int) ahead.y, (int) ahead.z);
					waypoints.add("Шахта", mc.player.getBlockX() + 400, 40, mc.player.getBlockZ() - 250);
				}
			}
			case 1 -> {
				// pretend the summoned pig was hit, for TargetESP / TargetHUD
				mc.level.getEntitiesOfClass(Pig.class, mc.player.getBoundingBox().inflate(8), e -> true)
					.stream().findFirst().ifPresent(TargetTracker::onAttack);
			}
			case 2 -> {
				// hurt the pig (DamageNumbers), screenshot while the number is visible
				runCommand(mc, "damage @e[type=pig,limit=1,sort=nearest] 3");
				pendingShot = "zv_world_hud";
				pendingShotTicks = 10;
			}
			case 3 -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				movingTicks = 25;
			}
			case 4 -> shot(mc, "zv_world_cape");
			case 5 -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
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
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				// ChatHeads: a short message and one long enough to wrap
				mc.player.connection.sendChat("Привет! Это ChatHeads");
				mc.player.connection.sendChat("Длинное сообщение, чтобы проверить перенос строк: голова только у первой строки, а текст всех строк выровнен");
				mc.gui.setScreen(new ChatScreen("", false));
			}
			case 8 -> shot(mc, "zv_world_chat");
			case 9 -> mc.gui.setScreen(new com.zerovisual.gui.WaypointsScreen(null));
			case 10 -> shot(mc, "zv_world_waypoints");
			case 11 -> {
				mc.gui.setScreen(null);
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
				mc.player.setXRot(75);
				var jump = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.JumpCircles.class);
				jump.setEnabled(true);
				jump.mode.set("Star");
				jump.spawn(mc.player.position());
				pendingShot = "zv_world_star";
				pendingShotTicks = 5;
			}
			case 15 -> {
				var jump = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.JumpCircles.class);
				jump.mode.set("Shatter");
				jump.breakAfter.set(0.3);
				jump.spawn(mc.player.position());
				// 0.25 s appear + 0.3 s whole, then a quarter of the shatter
				pendingShot = "zv_world_shatter";
				pendingShotTicks = 14;
			}
			case 16 -> {
				// KillEffect black hole: a pig on the platform in front of the camera
				mc.player.setXRot(10);
				Vec3 front = mc.player.position().add(mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(4));
				runCommand(mc, String.format(java.util.Locale.ROOT, "summon pig %.2f %.2f %.2f {NoAI:1b}", front.x, front.y, front.z));
				var kill = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.KillEffect.class);
				kill.setEnabled(true);
				kill.style.set("Black hole");
			}
			case 17 -> {
				mc.level.getEntitiesOfClass(Pig.class, mc.player.getBoundingBox().inflate(8), e -> true)
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
				mc.player.setYRot(0);
				mc.player.setXRot(-35);
			}
			case 21 -> shot(mc, "zv_sky_galaxy");
			case 22 -> {
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.CustomSky.class).mode.set("Realistic");
				runCommand(mc, "time set 12400");
				mc.player.setYRot(90);
				mc.player.setXRot(-10);
			}
			case 23 -> shot(mc, "zv_sky_sunset");
			case 24 -> {
				var sky = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.CustomSky.class);
				sky.setEnabled(true);
				sky.mode.set("Black hole");
				// clean shot for banners: no HUD, no hand, high above the trees, nothing else in view
				hideGui(mc, true);
				var m = ZeroVisualClient.get().modules();
				m.get(com.zerovisual.module.impl.utilities.Waypoints.class).setEnabled(false);
				m.get(com.zerovisual.module.impl.cosmetics.Pet.class).setEnabled(false);
				String who = mc.player.getName().getString();
				runCommand(mc, "effect clear " + who);
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "execute at " + who + " run fill ~-2 140 ~-2 ~2 140 ~2 minecraft:barrier");
				runCommand(mc, "execute at " + who + " run tp " + who + " ~ 141 ~");
				mc.player.setYRot(-143.7f);
				mc.player.setXRot(-12);
			}
			case 25 -> {
				shot(mc, "zv_sky_blackhole");
				hideGui(mc, false);
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
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setYRot(30);
				mc.player.setXRot(15);
				// walk so the pet runs after the player
				movingTicks = 40;
			}
			case 27 -> shot(mc, "zv_pet");
			case 28 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
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
				hideGui(mc, true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(25);
				var pet = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Pet.class);
				pet.setEnabled(true);
				pet.type.set("Mini me");
				pet.armor.set("Diamond");
			}
			case 34 -> {
				// put the pet beside the player and a bit towards the camera, so it is in the frame
				double yaw = Math.toRadians(mc.player.getYRot());
				Vec3 side = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(1.3);
				Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw)).scale(0.6);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Pet.class)
					.placeAt(mc.player.position().add(side).add(back), mc.player.getYRot() + 180);
			}
			case 35 -> shot(mc, "zv_pet_stand");
			case 36 -> {
				// turn around: the pet runs to the player
				mc.player.setYRot(mc.player.getYRot() + 150);
				mc.player.setYBodyRot(mc.player.getYRot());
				timedShots.put(ticks + 6, "zv_pet_run");
			}
			case 37, 38, 39 -> {
				// stand still so Mini me sits down
			}
			case 40 -> shot(mc, "zv_pet_sit");
			case 41 -> {
				// ItemPhysics: throw the held stack and catch it in the air
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.ItemPhysics.class).setEnabled(true);
				hideGui(mc, true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(-5);
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "item replace entity " + who + " weapon.mainhand with minecraft:golden_apple 16");
			}
			case 42 -> {
				mc.gameMode.dropItem(mc.player, true);
				timedShots.put(ticks + 4, "zv_items_fly");
				timedShots.put(ticks + 8, "zv_items_fly2");
			}
			case 43 -> hideGui(mc, false);
			case 44 -> {
				// Mini me close-up for banners: on a platform in the sky, right in front of the camera
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "execute at " + who + " run fill ~-4 150 ~-4 ~4 150 ~4 minecraft:barrier");
				runCommand(mc, "execute at " + who + " run tp " + who + " ~ 151 ~ 0 40");
				hideGui(mc, true);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				var pet = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Pet.class);
				pet.setEnabled(true);
				pet.type.set("Mini me");
				pet.armor.set("Diamond");
			}
			case 45 -> {
				mc.player.setYRot(0);
				mc.player.setXRot(40);
				var pet = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Pet.class);
				// 2 blocks south (in front, yaw 0), facing back north at the player
				pet.placeAt(mc.player.position().add(0, 0, 2), 180);
				timedShots.put(ticks + 70, "zv_mini_arms");
				timedShots.put(ticks + 210, "zv_mini_tap");
				timedShots.put(ticks + 330, "zv_mini_sit");
			}
			case 46, 47, 48, 49, 50, 51, 52, 53, 54, 55 -> {
				// wait for the idle animations
			}
			case 56 -> hideGui(mc, false);
			case 57 -> {
				// TotemPop: the HUD animation on its own, then a real totem pop
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				var totem = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.TotemPop.class);
				totem.setEnabled(true);
				totem.startAnimation();
				timedShots.put(ticks + 5, "zv_totem_anim1");
				timedShots.put(ticks + 16, "zv_totem_anim2");
				String who = mc.player.getName().getString();
				runCommand(mc, "gamemode survival " + who);
				runCommand(mc, "item replace entity " + who + " weapon.mainhand with minecraft:totem_of_undying");
				runCommand(mc, "item replace entity " + who + " weapon.offhand with minecraft:totem_of_undying");
			}
			case 58 -> {
				runCommand(mc, "damage " + mc.player.getName().getString() + " 40");
				timedShots.put(ticks + 5, "zv_totem_pop1");
				timedShots.put(ticks + 16, "zv_totem_pop2");
			}
			case 59 -> {
				// HitboxColor: a pig right under the crosshair
				hideGui(mc, false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.HitboxColor.class).setEnabled(true);
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				mc.player.setXRot(35);
				// exactly on the line of sight, 2.5 blocks ahead (the pig is 0.9 high)
				var spot = mc.player.getEyePosition().add(mc.player.getLookAngle().multiply(2.5, 2.5, 2.5)).add(0, -0.45, 0);
				runCommand(mc, String.format(java.util.Locale.ROOT, "summon minecraft:pig %.2f %.2f %.2f {NoAI:1b}", spot.x, spot.y, spot.z));
				timedShots.put(ticks + 15, "zv_hitbox");
			}
			case 60 -> {
				// SmoothSwap and ItemZoom: shift click a stack in the inventory (slow flight so a screenshot catches it)
				var modules = ZeroVisualClient.get().modules();
				modules.get(com.zerovisual.module.impl.visuals.SmoothSwap.class).setEnabled(true);
				modules.get(com.zerovisual.module.impl.visuals.SmoothSwap.class).duration.set(400.0);
				modules.get(com.zerovisual.module.impl.visuals.ItemZoom.class).setEnabled(true);
				runCommand(mc, "item replace entity " + mc.player.getName().getString() + " hotbar.0 with minecraft:diamond 32");
				mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
			}
			case 61 -> {
				mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 36, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
				timedShots.put(ticks + 3, "zv_swap1");
				timedShots.put(ticks + 5, "zv_swap2");
			}
			case 62 -> {
				shot(mc, "zv_swap_done");
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.SmoothSwap.class).duration.set(160.0);
				mc.gui.setScreen(null);
			}
			case 63 -> {
				// Play With Friends: open the world to friends, a pretend friend joins through the tunnel and pings the world
				hideGui(mc, false);
				mc.gui.setScreen(com.zerovisual.gui.PwfScreen.host(null));
			}
			case 64 -> {
				// the Without license option, as the user would switch it on
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.utilities.PlayWithFriends.class).noLicense.set(true);
				mc.getSingleplayerServer().setUsesAuthentication(false);
				com.zerovisual.pwf.PwfSelfTest.run(com.zerovisual.pwf.PwfManager.currentHost(mc.getSingleplayerServer().getPort()), mc.getSingleplayerServer().getPort(), ZeroVisualClient.LOGGER::info);
				timedShots.put(ticks + 4, "zv_pwf_invite");
			}
			case 65, 66 -> {
				// waiting for the connection
			}
			case 67 -> shot(mc, "zv_pwf_connected");
			case 68 -> {
				// ShieldColor: own shield in the off hand, a husk with a shield in front (it does not burn in the sun)
				mc.gui.setScreen(null);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.ShieldColor.class).setEnabled(true);
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "item replace entity " + who + " weapon.offhand with minecraft:shield");
				mc.player.setXRot(10);
				Vec3 spot = mc.player.position().add(mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
				runCommand(mc, String.format(java.util.Locale.ROOT, "summon minecraft:husk %.2f %.2f %.2f {NoAI:1b,Silent:1b,equipment:{mainhand:{id:\"minecraft:shield\",count:1}},Rotation:[%.1ff,0f]}",
					spot.x, spot.y, spot.z, mc.player.getYRot() + 180));
				timedShots.put(ticks + 230, "zv_shield_ready");
			}
			case 69, 70, 71, 72, 73, 74, 75, 76 -> {
				// the chat fades out
			}
			case 77 -> {
				// an axe hit: own shield on cooldown, the husk's marked as broken
				mc.player.getCooldowns().addCooldown(mc.player.getOffhandItem(), 100);
				var shield = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.ShieldColor.class);
				mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, mc.player.getBoundingBox().inflate(8)).forEach(shield::onShieldBreak);
				timedShots.put(ticks + 5, "zv_shield_broken");
			}
			case 78 -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				timedShots.put(ticks + 10, "zv_shield_f5");
			}
			case 79, 80 -> {
				// the 5 second cooldown runs out
			}
			case 81 -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				timedShots.put(ticks + 5, "zv_shield_after");
			}
			case 82 -> {
			}
			case 83 -> {
				// promo shot for the banner: two husks, one shield ready, one broken, no HUD
				mc.gui.setScreen(null);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				hideGui(mc, true);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.ShieldColor.class).setEnabled(true);
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "time set 6000");
				runCommand(mc, "weather clear");
				mc.player.setXRot(8);
			}
			case 84 -> {
				// summoned a step later: the poof of the killed mobs is gone by the time of the shot
				Vec3 look = mc.player.getLookAngle().multiply(1, 0, 1).normalize();
				Vec3 right = new Vec3(-look.z, 0, look.x);
				for (int side = -1; side <= 1; side += 2) {
					Vec3 spot = mc.player.position().add(look.scale(2.6)).add(right.scale(side * 0.75));
					runCommand(mc, String.format(java.util.Locale.ROOT, "summon minecraft:husk %.2f %.2f %.2f {NoAI:1b,Silent:1b,equipment:{mainhand:{id:\"minecraft:shield\",count:1}},Rotation:[%.1ff,0f]}",
						spot.x, spot.y, spot.z, mc.player.getYRot() + 180));
				}
			}
			case 85, 86 -> {
			}
			case 87 -> {
				Vec3 look = mc.player.getLookAngle().multiply(1, 0, 1).normalize();
				Vec3 right = new Vec3(-look.z, 0, look.x);
				var shield = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.ShieldColor.class);
				mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, mc.player.getBoundingBox().inflate(8)).stream()
					.filter(h -> h.position().subtract(mc.player.position()).dot(right) > 0)
					.forEach(shield::onShieldBreak);
				timedShots.put(ticks + 5, "zv_promo_shield");
			}
			case 88 -> hideGui(mc, false);
			case 89 -> {
				// emotes: every emote from the front, on open ground without the HUD
				mc.gui.setScreen(null);
				hideGui(mc, true);
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "time set 6000");
				runCommand(mc, "execute at " + who + " run fill ~-4 150 ~-4 ~4 150 ~4 minecraft:smooth_stone");
				runCommand(mc, "execute at " + who + " run tp " + who + " ~ 151 ~");
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setXRot(0);
				for (var m : ZeroVisualClient.get().modules().getModules()) {
					String n = m.getName();
					if (n.equals("HitParticles") || n.equals("KillEffect") || n.equals("Nametags") || n.equals("TotemPop") || n.equals("JumpCircles")) m.setEnabled(false);
				}
			}
			case 90, 91 -> {
				// the poof of the killed mobs fades
			}
			case 92, 93, 94, 95, 96, 97, 98, 99, 100, 101 -> {
				var emote = com.zerovisual.emote.Emotes.ALL.get(step - 92);
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), emote);
				timedShots.put(ticks + 14, "zv_emote_" + emote.id());
			}
			case 102 -> {
				com.zerovisual.emote.EmoteManager.clear();
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				hideGui(mc, false);
			}
			case 103 -> {
				com.zerovisual.gui.EmoteWheelScreen.keepOpen = true;
				mc.gui.setScreen(new com.zerovisual.gui.EmoteWheelScreen(ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.EmoteMenu.class)));
				timedShots.put(ticks + 15, "zv_emote_wheel");
			}
			case 104 -> {
				com.zerovisual.gui.EmoteWheelScreen.keepOpen = false;
				mc.gui.setScreen(null);
			}
			case 105 -> {
				// new effects of 0.4.0: a clean platform, the sky on, the HUD only where it matters
				mc.gui.setScreen(null);
				String who = mc.player.getName().getString();
				var m = ZeroVisualClient.get().modules();
				m.get(com.zerovisual.module.impl.visuals.TargetEsp.class).setEnabled(true);
				m.get(com.zerovisual.module.impl.visuals.JumpCircles.class).setEnabled(true);
				m.get(com.zerovisual.module.impl.cosmetics.KillEffect.class).setEnabled(true);
				m.get(com.zerovisual.module.impl.cosmetics.Wings.class).setEnabled(true);
				m.get(com.zerovisual.module.impl.visuals.Crosshair.class).setEnabled(true);
				m.get(com.zerovisual.module.impl.visuals.CustomSky.class).setEnabled(false);
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "time set 6000");
				runCommand(mc, "execute at " + who + " run fill ~-10 150 ~-10 ~10 150 ~10 minecraft:grass_block");
				runCommand(mc, "execute at " + who + " run tp " + who + " ~ 151 ~");
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(30);
			}
			case 106 -> {
				var jump = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.JumpCircles.class);
				jump.mode.set("Glyphs");
				jump.radius.set(2.0);
				jump.duration.set(2.0);
				jump.spawn(mc.player.position());
				timedShots.put(ticks + 22, "zv_fx_glyphs");
			}
			case 107 -> {
				var jump = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.JumpCircles.class);
				jump.mode.set("Block wave");
				jump.spawn(mc.player.position());
				timedShots.put(ticks + 14, "zv_fx_wave");
			}
			case 108 -> {
				mc.player.setXRot(10);
				var ahead = mc.player.getLookAngle().multiply(1, 0, 1).normalize();
				var at = mc.player.position().add(ahead.scale(4.5)).add(-ahead.z * 2.2, 0, ahead.x * 2.2);
				runCommand(mc, String.format(java.util.Locale.ROOT, "summon minecraft:husk %.2f %.2f %.2f {NoAI:1b,Silent:1b}", at.x, at.y, at.z));
			}
			case 109 -> {
				var esp = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.TargetEsp.class);
				esp.style.set("Crystals");
				esp.keepTime.set(10.0);
				mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, mc.player.getBoundingBox().inflate(10)).stream().findFirst().ifPresent(TargetTracker::onAttack);
				timedShots.put(ticks + 15, "zv_fx_crystals");
			}
			case 110 -> {
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.TargetEsp.class).style.set("Pulse");
				timedShots.put(ticks + 12, "zv_fx_pulse");
			}
			case 111 -> {
				runCommand(mc, "damage @e[type=husk,limit=1] 1");
				timedShots.put(ticks + 4, "zv_fx_pulse_hit");
			}
			case 112 -> {
				var kill = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.KillEffect.class);
				kill.style.set("Soul");
				mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, mc.player.getBoundingBox().inflate(10)).stream().findFirst().ifPresent(kill::trigger);
				timedShots.put(ticks + 22, "zv_fx_soul");
			}
			case 113 -> {
				var wings = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Wings.class);
				wings.style.set("Dragon");
				hideGui(mc, true);
				timedShots.put(ticks + 12, "zv_fx_dragon");
			}
			case 114 -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(-28);
				var sky = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.CustomSky.class);
				sky.setEnabled(true);
				sky.mode.set("Rifts");
				timedShots.put(ticks + 20, "zv_fx_rifts");
			}
			case 115, 116, 117, 118, 119 -> {
				hideGui(mc, false);
				mc.player.setXRot(0);
				var cross = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.Crosshair.class);
				String style = new String[]{"T", "Square", "Brackets", "Chevron", "Diamond"}[step - 115];
				cross.style.set(style);
				cross.size.set(6.0);
				timedShots.put(ticks + 6, "zv_fx_cross_" + style.toLowerCase());
			}
			case 120, 121, 122, 123 -> {
				var anim = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.Animations.class);
				anim.setEnabled(true);
				anim.mode.set(new String[]{"Stab", "Slam", "Backhand", "Twirl"}[step - 120]);
				anim.swingSpeed.set(0.4);
				mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
				timedShots.put(ticks + 5, "zv_fx_anim_" + (step - 120));
			}
			case 124 -> {
				hideGui(mc, true);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(-34);
				timedShots.put(ticks + 6, "zv_fx_rifts_a");
			}
			case 125 -> {
				mc.player.setYRot(mc.player.getYRot() + 14);
				timedShots.put(ticks + 6, "zv_fx_rifts_b");
			}
			case 126 -> {
				// the dances against their reference videos: eight moments of each, from the front
				mc.gui.setScreen(null);
				hideGui(mc, true);
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "time set 6000");
				runCommand(mc, "execute at " + who + " run fill ~-4 150 ~-4 ~4 150 ~4 minecraft:smooth_stone");
				runCommand(mc, "execute at " + who + " run tp " + who + " ~ 151 ~");
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.CustomSky.class).setEnabled(false);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Wings.class).setEnabled(false);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.EmoteMenu.class).setEnabled(false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setXRot(0);
				for (var m : ZeroVisualClient.get().modules().getModules()) {
					String n = m.getName();
					if (n.equals("HitParticles") || n.equals("KillEffect") || n.equals("Nametags") || n.equals("TotemPop") || n.equals("JumpCircles")) m.setEnabled(false);
				}
			}
			case 127, 128 -> {
			}
			case 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144,
				145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160 -> {
				int n = step - 129, k = n % 8;
				String id = new String[]{"rat", "rampage", "funk", "squat"}[n / 8];
				var emote = com.zerovisual.emote.Emotes.byId(id);
				mc.player.yBodyRot = mc.player.yBodyRotO = mc.player.getYRot();
				if (k == 0) com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), emote);
				com.zerovisual.emote.EmoteManager.frozenAt = emote.duration() * (1 + k / 8f);
				timedShots.put(ticks + 8, "zv_dance_" + id + "_" + k);
			}
			case 161 -> {
				// the sit pose from the side and the cape from behind
				mc.player.yBodyRot = mc.player.yBodyRotO = mc.player.getYRot() + 90;
				mc.player.setYRot(mc.player.getYRot() + 90);
				var sit = com.zerovisual.emote.Emotes.byId("sit");
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), sit);
				com.zerovisual.emote.EmoteManager.frozenAt = 1f;
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setYRot(mc.player.getYRot() + 90);
				timedShots.put(ticks + 8, "zv_dance_sit_side");
			}
			case 162 -> {
				var funk = com.zerovisual.emote.Emotes.byId("rampage");
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), funk);
				com.zerovisual.emote.EmoteManager.frozenAt = 1.1f;
				mc.player.yBodyRot = mc.player.yBodyRotO = mc.player.getYRot();
				timedShots.put(ticks + 8, "zv_dance_cape");
			}
			case 163 -> {
				com.zerovisual.emote.EmoteManager.frozenAt = -1;
				hideGui(mc, false);
			}
			case 164 -> {
				// the block wave up close, slowed down
				mc.gui.setScreen(null);
				hideGui(mc, true);
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=!player]");
				runCommand(mc, "time set 6000");
				runCommand(mc, "execute at " + who + " run fill ~-10 150 ~-10 ~10 150 ~10 minecraft:grass_block");
				runCommand(mc, "execute at " + who + " run fill ~3 151 ~-10 ~10 151 ~10 minecraft:grass_block");
				runCommand(mc, "execute at " + who + " run tp " + who + " ~ 151 ~");
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(18);
				for (var m : ZeroVisualClient.get().modules().getModules()) {
					String n = m.getName();
					if (n.equals("HitParticles") || n.equals("KillEffect") || n.equals("Nametags") || n.equals("TotemPop") || n.equals("Wings") || n.equals("Pet")) m.setEnabled(false);
				}
			}
			case 165, 166 -> {
			}
			case 167 -> {
				var jump = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.JumpCircles.class);
				jump.setEnabled(true);
				jump.mode.set("Block wave");
				jump.radius.set(1.2);
				jump.duration.set(3.0);
				jump.spawn(mc.player.position());
				for (int k = 0; k < 6; k++) timedShots.put(ticks + 4 + k * 8, "zv_wave_" + k);
			}
			case 168, 169 -> {
			}
			case 170 -> {
				// Mini me idles from the front, and rift colors
				var pet = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Pet.class);
				pet.setEnabled(true);
				pet.type.set("Mini me");
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(30);
				var ahead = mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(2);
				pet.placeAt(mc.player.position().add(ahead), mc.player.getYRot() + 180);
				for (int k = 0; k < 4; k++) timedShots.put(ticks + 60 + k * 2, "zv_mini_arms" + k);
				for (int k = 0; k < 4; k++) timedShots.put(ticks + 170 + k * 2, "zv_mini_tap" + k);
			}
			case 171, 172, 173, 174, 175, 176 -> {
			}
			case 177 -> {
				var sky = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.CustomSky.class);
				sky.setEnabled(true);
				sky.mode.set("Rifts");
				mc.player.setXRot(-34);
				timedShots.put(ticks + 8, "zv_rift_default");
			}
			case 178 -> {
				var sky = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.CustomSky.class);
				sky.riftColor1.set(0xFFF97316);
				sky.riftColor2.set(0xFFFACC15);
				sky.riftColor3.set(0xFF22C55E);
				timedShots.put(ticks + 8, "zv_rift_custom");
			}
			case 180 -> {
				// Mini me during a long emote: the moment it used to sit down, and its idles after the emote
				var modules = ZeroVisualClient.get().modules();
				modules.get(com.zerovisual.module.impl.visuals.CustomSky.class).setEnabled(false);
				modules.get(com.zerovisual.module.impl.cosmetics.Wings.class).setEnabled(false);
				var pet = modules.get(com.zerovisual.module.impl.cosmetics.Pet.class);
				pet.setEnabled(true);
				pet.type.set("Mini me");
				mc.gui.setScreen(null);
				hideGui(mc, true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(22);
				var ahead = mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6);
				pet.placeAt(mc.player.position().add(ahead), mc.player.getYRot() + 180);
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), com.zerovisual.emote.Emotes.byId("rampage"));
				timedShots.put(ticks + 320, "zv_pet_emote");
			}
			case 181, 182, 183, 184, 185, 186, 187, 188, 189, 190 -> {
			}
			case 191 -> {
				com.zerovisual.emote.EmoteManager.stop(mc.player.getUUID());
				timedShots.put(ticks + 100, "zv_pet_after");
			}
			case 192, 193, 194, 195 -> {
			}
			case 196 -> {
				// hat and halo on the head during emotes
				hideGui(mc, true);
				for (var m : ZeroVisualClient.get().modules().getModules()) if (m.getName().equals("Nametags")) m.setEnabled(false);
				var modules = ZeroVisualClient.get().modules();
				modules.get(com.zerovisual.module.impl.visuals.ChinaHat.class).setEnabled(true);
				modules.get(com.zerovisual.module.impl.cosmetics.Halo.class).setEnabled(true);
				modules.get(com.zerovisual.module.impl.cosmetics.Pet.class).setEnabled(false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setXRot(12);
				mc.player.yBodyRot = mc.player.yBodyRotO = mc.player.getYRot();
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), com.zerovisual.emote.Emotes.byId("lie"));
				com.zerovisual.emote.EmoteManager.frozenAt = 1f;
				timedShots.put(ticks + 8, "zv_hat_lie");
			}
			case 197 -> {
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), com.zerovisual.emote.Emotes.byId("sit"));
				timedShots.put(ticks + 8, "zv_hat_sit");
			}
			case 198 -> {
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), com.zerovisual.emote.Emotes.byId("rampage"));
				com.zerovisual.emote.EmoteManager.frozenAt = 3.5f;
				timedShots.put(ticks + 8, "zv_hat_dance");
			}
			case 199 -> {
				com.zerovisual.emote.EmoteManager.clear();
				com.zerovisual.emote.EmoteManager.frozenAt = -1;
				timedShots.put(ticks + 8, "zv_hat_rest");
			}
			case 200 -> {
				// wings on the back during emotes, seen from behind
				hideGui(mc, true);
				var wings = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Wings.class);
				wings.setEnabled(true);
				wings.style.set("Dragon");
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.ChinaHat.class).setEnabled(false);
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.Halo.class).setEnabled(false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(16);
				mc.player.setYRot(mc.player.getYRot() + 40);
				mc.player.yBodyRot = mc.player.yBodyRotO = mc.player.getYRot() - 40;
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), com.zerovisual.emote.Emotes.byId("sit"));
				com.zerovisual.emote.EmoteManager.frozenAt = 1f;
				timedShots.put(ticks + 8, "zv_wings_sit");
			}
			case 201 -> {
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), com.zerovisual.emote.Emotes.byId("lie"));
				timedShots.put(ticks + 8, "zv_wings_lie");
			}
			case 202 -> {
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), com.zerovisual.emote.Emotes.byId("rampage"));
				com.zerovisual.emote.EmoteManager.frozenAt = 3.5f;
				timedShots.put(ticks + 8, "zv_wings_dance");
			}
			case 203 -> {
				com.zerovisual.emote.EmoteManager.clear();
				com.zerovisual.emote.EmoteManager.frozenAt = -1;
				timedShots.put(ticks + 8, "zv_wings_rest");
			}
			case 204 -> {
				// promo shot for the 0.4.0 banner: a dance under the rifts
				hideGui(mc, true);
				var modules = ZeroVisualClient.get().modules();
				for (var m : modules.getModules()) {
					String n = m.getName();
					if (n.equals("Nametags") || n.equals("Pet") || n.equals("HitParticles")) m.setEnabled(false);
				}
				var sky = modules.get(com.zerovisual.module.impl.visuals.CustomSky.class);
				sky.setEnabled(true);
				sky.mode.set("Rifts");
				var wings = modules.get(com.zerovisual.module.impl.cosmetics.Wings.class);
				wings.setEnabled(true);
				wings.style.set("Dragon");
				modules.get(com.zerovisual.module.impl.visuals.ChinaHat.class).setEnabled(true);
				String who = mc.player.getName().getString();
				runCommand(mc, "kill @e[type=item]");
				runCommand(mc, "kill @e[type=pig]");
				runCommand(mc, "kill @e[type=husk]");
				runCommand(mc, "time set 6000");
				runCommand(mc, "item replace entity " + who + " weapon.offhand with air");
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setXRot(-20);
				mc.player.yBodyRot = mc.player.yBodyRotO = mc.player.getYRot();
				com.zerovisual.emote.EmoteManager.play(mc.player.getUUID(), com.zerovisual.emote.Emotes.byId("rampage"));
			}
			case 205, 206 -> {
				// the poof of the removed mobs fades; a narrow view brings the dancer close
				mc.options.fov().set(52);
				mc.player.setXRot(16);
			}
			case 207, 208, 209 -> {
				float[] moments = {7.3f, 9.97f, 3.6f};
				com.zerovisual.emote.EmoteManager.frozenAt = moments[step - 207];
				timedShots.put(ticks + 10, "zv_promo040_" + (step - 207));
			}
			case 210 -> {
				com.zerovisual.emote.EmoteManager.frozenAt = -1;
				com.zerovisual.emote.EmoteManager.clear();
				mc.options.fov().set(70);
				hideGui(mc, false);
			}
			case 211 -> {
				// HudStyle and the effect icons of Potions
				mc.gui.setScreen(null);
				hideGui(mc, false);
				String who = mc.player.getName().getString();
				runCommand(mc, "effect give " + who + " minecraft:speed 300 1");
				runCommand(mc, "effect give " + who + " minecraft:strength 300 0");
				runCommand(mc, "effect give " + who + " minecraft:regeneration 300 2");
				ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.hud.Potions.class).setEnabled(true);
				timedShots.put(ticks + 25, "zv_hud_default");
			}
			case 212 -> {
				var style = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.hud.HudStyle.class);
				style.outline.set(true);
				style.outlineWidth.set(1.5);
				style.radius.set(2.0);
				style.opacity.set(0.5);
				style.background.set(0xFF1E3A8A);
				timedShots.put(ticks + 6, "zv_hud_custom");
			}
			case 213 -> {
				var style = ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.hud.HudStyle.class);
				style.outline.set(false);
				style.outlineWidth.set(1.0);
				style.radius.set(6.0);
				style.opacity.set(0.88);
				style.background.set(0xFF0D0D12);
			}
			default -> mc.stop();
		}
		step++;
		if (step == 1 && startFrom > 1) step = startFrom;
	}

	private static void hideGui(Minecraft mc, boolean hidden) {
		if (mc.gui.hud.isHidden() != hidden) mc.gui.hud.toggle();
	}

	private static void runCommand(Minecraft mc, String command) {
		var server = mc.getSingleplayerServer();
		if (server == null) return;
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
	}

	/** Drops a few items in front of the player via the integrated server (to see ItemPhysics). */
	private static void summonItems(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (server == null) return;
		Vec3 front = mc.player.position().add(mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
		String[] items = {"diamond_sword", "golden_apple", "grass_block", "ender_pearl"};
		String player = mc.player.getName().getString();
		server.execute(() -> {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "give " + player + " minecraft:totem_of_undying 3");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "give " + player + " minecraft:diamond_pickaxe 1");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "item replace entity " + player + " armor.chest with minecraft:diamond_chestplate");
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "item replace entity " + player + " armor.head with minecraft:iron_helmet");
			// survival shows the hunger bar (Saturation)
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamemode survival " + player);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), String.format(java.util.Locale.ROOT,
				"summon pig %.2f %.2f %.2f {NoAI:1b}", front.x + 1.5, front.y + 0.5, front.z));
			for (int i = 0; i < items.length; i++) {
				String command = String.format(java.util.Locale.ROOT, "summon item %.2f %.2f %.2f {Item:{id:\"minecraft:%s\",count:1}}",
					front.x + (i - 1.5) * 0.6, front.y + 0.5, front.z, items[i]);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
			}
		});
	}

	private static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
			message -> ZeroVisualClient.LOGGER.info("Dev screenshot: {}", message.getString()));
	}
}
