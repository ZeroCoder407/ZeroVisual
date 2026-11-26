package com.zerovisual.util;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.HudModule;
import com.zerovisual.module.Module;
import com.zerovisual.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityPig;

import java.util.ArrayList;
import java.util.List;

/**
 * Development only (ZV_DEVSHOTS=modules): after the test world is up, turns on one module at a time in its own
 * scene and saves a screenshot of each, zv_mod_&lt;name&gt;.png. Used to check the world effects in a real launch.
 */
final class DevModuleShots {
	private final Minecraft mc = Minecraft.getMinecraft();
	private final List<Scene> scenes = new ArrayList<Scene>();
	private int index = -1, tick;

	private static final class Scene {
		final String name;
		final Runnable setup, everyTick;
		final int ticks;

		Scene(String name, int ticks, Runnable setup, Runnable everyTick) {
			this.name = name;
			this.ticks = ticks;
			this.setup = setup;
			this.everyTick = everyTick;
		}
	}

	DevModuleShots() {
		scene("customsky_rifts", 40, () -> {
			on("CustomSky", "Mode", "Rifts");
			view(1, -30);
		}, null);
		scene("customsky_blackhole_night", 40, () -> {
			on("CustomSky", "Mode", "Black hole");
			view(0, -21);
			mc.thePlayer.rotationYaw = (float) Math.toDegrees(Math.atan2(-0.55, -0.75));
			mc.thePlayer.sendChatMessage("/time set 18000");
		}, null);
		scene("customsky_blackhole_day", 30, () -> {
			on("CustomSky", "Mode", "Black hole");
			view(0, -21);
			mc.thePlayer.rotationYaw = (float) Math.toDegrees(Math.atan2(-0.55, -0.75));
			mc.thePlayer.sendChatMessage("/time set 6000");
		}, null);
		scene("motionblur_still", 40, () -> {
			on("MotionBlur", "Strength", 0.9);
			view(0, 10);
		}, null);
		scene("after_rifts", 20, () -> view(1, 30), null);
		scene("nametags", 30, () -> {
			on("Nametags", "Self", true);
			view(2, 10);
		}, null);
		scene("nametags_night", 40, () -> {
			on("Nametags", "Self", true);
			on("Halo");
			view(2, 10);
			mc.thePlayer.sendChatMessage("/time set 18000");
		}, null);
		scene("night_back_to_day", 10, () -> mc.thePlayer.sendChatMessage("/time set 6000"), null);
		scene("halo", 30, () -> {
			on("Halo");
			view(2, 20);
		}, null);
		scene("wings_dragon", 30, () -> {
			on("Wings", "Style", "Dragon");
			view(1, 25);
		}, null);
		scene("wings_side", 30, () -> {
			on("Wings", "Style", "Dragon");
			view(1, 25);
			mc.thePlayer.rotationYaw += 70;
		}, null);
		scene("jump_glyphs", 12, () -> {
			on("JumpCircles", "Mode", "Glyphs");
			view(1, 45);
			jump();
		}, null);
		scene("jump_wave", 10, () -> {
			on("JumpCircles", "Mode", "Block wave");
			view(1, 45);
			jump();
		}, null);
		scene("killeffect_blackhole", 14, () -> {
			on("KillEffect", "Style", "Black hole");
			view(0, 0);
			kill();
		}, null);
		scene("pet_minime", 60, () -> {
			on("Pet", "Type", "Mini me");
			view(1, 35);
		}, null);
		scene("pet_firstperson", 20, () -> {
			on("Pet", "Type", "Mini me");
			view(0, 20);
		}, null);
		scene("firstperson_vanilla", 15, () -> view(0, 20), null);
		scene("viewmodel", 15, () -> {
			on("ViewModel", "X", -0.3, "Y", 0.1, "Scale", 0.7);
			view(0, 20);
		}, null);
		scene("animations_swing", 3, () -> {
			on("Animations", "Style", "Smooth");
			view(0, 20);
			mc.thePlayer.swingItem();
		}, null);
		scene("animations_block", 6, () -> {
			on("Animations", "Style", "Smooth");
			view(0, 20);
			useItem(true);
		}, () -> useItem(true));
		scene("aspectratio", 15, () -> {
			useItem(false);
			on("AspectRatio", "Ratio", 1.0);
			view(0, 10);
		}, null);
		scene("motionblur", 20, () -> {
			on("MotionBlur", "Strength", 0.9);
			view(0, 10);
		}, () -> mc.thePlayer.rotationYaw += 12);
		scene("cape_emote", 30, () -> {
			on("Cape");
			view(2, 10);
			com.zerovisual.emote.EmoteManager.play(mc.thePlayer.getUniqueID(), com.zerovisual.emote.Emotes.byId("rampage"));
		}, null);
		scene("cape_emote_back", 15, () -> {
			on("Cape");
			view(1, 10);
			com.zerovisual.emote.EmoteManager.play(mc.thePlayer.getUniqueID(), com.zerovisual.emote.Emotes.byId("rampage"));
		}, null);
		scene("cape_emote_side", 20, () -> {
			on("Cape");
			view(1, 10);
			mc.thePlayer.rotationYaw = 80;
			com.zerovisual.emote.EmoteManager.play(mc.thePlayer.getUniqueID(), com.zerovisual.emote.Emotes.byId("rampage"));
		}, null);
		scene("pet_then_arm", 40, () -> {
			on("Pet", "Type", "Mini me");
			view(1, 35);
		}, null);
		scene("pet_then_arm_fp", 10, () -> {
			on("Pet", "Type", "Mini me");
			mc.thePlayer.inventory.currentItem = 5;
			view(0, 20);
		}, null);
	}

	private void scene(String name, int ticks, Runnable setup, Runnable everyTick) {
		scenes.add(new Scene(name, ticks, setup, everyTick));
	}

	/** One step per tick; returns the screenshot to take, or null; finished when it returns "". */
	String tick() {
		if (index >= 0 && index < scenes.size()) {
			Scene s = scenes.get(index);
			if (s.everyTick != null) s.everyTick.run();
			tick++;
			if (tick == s.ticks) return "zv_mod_" + s.name;
			// the next scene only after the frame with the screenshot was drawn
			if (tick < s.ticks + 2) return null;
			index++;
			if (index < scenes.size()) start();
			return null;
		}
		if (index < 0) {
			mc.thePlayer.sendChatMessage("/give @p diamond_sword");
			mc.thePlayer.sendChatMessage("/time set 6000");
			mc.thePlayer.sendChatMessage("/gamerule doDaylightCycle false");
			index = 0;
			start();
			return null;
		}
		return "";
	}

	private void start() {
		for (Module m : ZeroVisualClient.get().modules().getModules()) {
			String n = m.getName();
			boolean keep = n.equals("ClickGUI") || n.equals("EmoteMenu") || n.equals("MainMenu") || n.equals("HudStyle")
				|| n.equals("Notifications") || n.equals("Watermark");
			if (!keep && m.isEnabled() && !(m instanceof HudModule && n.equals("Watermark"))) m.setEnabled(false);
		}
		com.zerovisual.emote.EmoteManager.stop(mc.thePlayer.getUniqueID());
		mc.thePlayer.inventory.currentItem = 0;
		mc.thePlayer.rotationYaw = 0;
		mc.gameSettings.fovSetting = 70;
		tick = 0;
		scenes.get(index).setup.run();
	}

	private void view(int perspective, float pitch) {
		mc.gameSettings.thirdPersonView = perspective;
		mc.thePlayer.rotationPitch = pitch;
	}

	private void useItem(boolean down) {
		net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), down);
	}

	private void jump() {
		mc.thePlayer.onGround = false;
		ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.visuals.JumpCircles.class).spawn(Vec3d.pos(mc.thePlayer));
	}

	private void kill() {
		EntityLivingBase pig = new EntityPig(mc.theWorld);
		pig.setPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ + 4);
		ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.cosmetics.KillEffect.class).trigger(pig);
	}

	/** Turns a module on with settings given as name, value pairs. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void on(String module, Object... settings) {
		for (Module m : ZeroVisualClient.get().modules().getModules()) {
			if (!m.getName().equals(module)) continue;
			for (int i = 0; i < settings.length; i += 2) {
				for (Setting s : m.getSettings()) {
					if (!s.getName().equals(settings[i])) continue;
					Object value = settings[i + 1];
					if (s instanceof com.zerovisual.setting.ModeSetting) ((com.zerovisual.setting.ModeSetting) s).set((String) value);
					else if (s instanceof com.zerovisual.setting.NumberSetting) s.set(((Number) value).doubleValue());
					else s.set(value);
				}
			}
			m.setEnabled(true);
		}
	}
}
