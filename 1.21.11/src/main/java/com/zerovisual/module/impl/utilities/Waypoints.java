package com.zerovisual.module.impl.utilities;

import com.mojang.blaze3d.platform.InputConstants;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.WaypointsScreen;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.KeySetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.waypoint.WaypointManager;
import com.zerovisual.waypoint.WaypointManager.Waypoint;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.zerovisual.render.WorldContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Your own waypoints, drawn in the world with name and distance (also through walls, like a minimap).
 * J opens the waypoint list. A waypoint is created where you die.
 */
public class Waypoints extends Module {
	private static KeyMapping openKey;

	public final KeySetting key = add(new KeySetting("Open list", () -> openKey));
	public final BooleanSetting deathPoint = add(new BooleanSetting("Death point", true));
	public final BooleanSetting showDistance = add(new BooleanSetting("Distance", true));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1, 0.5, 2, 0.1));

	private boolean wasDead;

	public Waypoints() {
		super("Waypoints", "Your own waypoints with distance (J opens the list by default)", Category.UTILITIES);
		setEnabled(true);
	}

	public static void registerKeyBinding() {
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.zerovisual.waypoints", InputConstants.Type.KEYSYM, InputConstants.KEY_J, com.zerovisual.ZeroVisualClient.KEY_CATEGORY));
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		while (openKey != null && openKey.consumeClick()) {
			if (mc.screen == null) mc.setScreen(new WaypointsScreen(null));
		}
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead && deathPoint.get()) {
			String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
			Waypoint waypoint = ZeroVisualClient.get().waypoints().add(I18n.get("zerovisual.waypoints.death", time),
				mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
			waypoint.color = 0xFFF43F5E;
			ZeroVisualClient.get().waypoints().save();
		}
		wasDead = dead;
	}

	/**
	 * Called while the world submits of a frame are collected. Markers and labels use the see-through
	 * pipelines, so they show through walls.
	 */
	public void render(WorldContext context) {
		if (mc.player == null) return;
		String dimension = WaypointManager.currentDimension();
		if (ZeroVisualClient.get().waypoints().current().stream().noneMatch(w -> w.visible && w.dimension.equals(dimension))) return;
		Matrix4f view = new Matrix4f(context.positionMatrix());
		Vec3 cam = context.camera().position();
		Font font = mc.font;

		for (Waypoint w : ZeroVisualClient.get().waypoints().current()) {
			if (!w.visible || !w.dimension.equals(dimension)) continue;
			Vec3 target = new Vec3(w.x + 0.5, w.y + 1.2, w.z + 0.5);
			Vec3 dir = target.subtract(cam);
			double distance = dir.length();
			if (distance < 0.5) continue;
			// far waypoints are drawn closer along the same direction so they stay inside the view distance
			double drawDistance = Math.min(distance, 48);
			Vec3 drawPos = cam.add(dir.scale(drawDistance / distance));
			float s = (float) (0.0035 * Math.max(drawDistance, 4) * scale.get());

			Matrix4f m = new Matrix4f(view)
				.translate((float) (drawPos.x - cam.x), (float) (drawPos.y - cam.y), (float) (drawPos.z - cam.z))
				.rotate(context.camera().rotation())
				.scale(s, -s, s);

			// diamond marker
			Matrix4f icon = new Matrix4f(m).translate(0, -10, 0).rotateZ((float) Math.toRadians(45));
			WorldRender.shape(icon, -4.5f, -4.5f, 9, 9, 2, 0, 4, ColorUtil.fade(w.color, 0.5f), ColorUtil.fade(w.color, 0.5f),
				ColorUtil.fade(w.color, 0.5f), ColorUtil.fade(w.color, 0.5f), true);
			WorldRender.shape(icon, -4.5f, -4.5f, 9, 9, 2, 0, 0, w.color, ColorUtil.brighten(w.color, 1.3f), w.color, ColorUtil.brighten(w.color, 1.3f), true);

			String label = showDistance.get() ? w.name + "  " + Math.round(distance) + "m" : w.name;
			Component text = Render2D.styled(label);
			WorldRender.text(m, text, -font.width(text) / 2f, 0, 0xFFFFFFFF, 0x90000000, true, false);
		}
	}
}
