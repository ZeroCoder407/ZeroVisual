package com.zerovisual.module.impl.utilities;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.WaypointsScreen;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.KeySetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.waypoint.WaypointManager;
import com.zerovisual.waypoint.WaypointManager.Waypoint;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Your own waypoints, drawn in the world with name and distance (also through walls, like a minimap).
 * J opens the waypoint list. A waypoint is created where you die.
 */
public class Waypoints extends Module {
	private static KeyBinding openKey;

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
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.zerovisual.waypoints", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.zerovisual"));
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		while (openKey != null && openKey.wasPressed()) {
			if (mc.currentScreen == null) mc.setScreen(new WaypointsScreen(null));
		}
		boolean dead = mc.player.isDead();
		if (dead && !wasDead && deathPoint.get()) {
			String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
			Waypoint waypoint = ZeroVisualClient.get().waypoints().add(I18n.translate("zerovisual.waypoints.death", time),
				mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
			waypoint.color = 0xFFF43F5E;
			ZeroVisualClient.get().waypoints().save();
		}
		wasDead = dead;
	}

	/**
	 * Called from WorldRenderEvents.END, after clouds and weather. The depth buffer is cleared first so markers and
	 * labels show through walls; the game clears depth again before drawing the hand, so nothing else is affected.
	 */
	public void render(WorldRenderContext context) {
		if (mc.player == null) return;
		String dimension = WaypointManager.currentDimension();
		if (ZeroVisualClient.get().waypoints().current().stream().noneMatch(w -> w.visible && w.dimension.equals(dimension))) return;
		RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT);
		// at END the camera rotation is no longer on the model-view stack, so it goes into our matrices
		Matrix4f view = new Matrix4f(context.positionMatrix());
		Vec3d cam = context.camera().getPos();
		VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
		TextRenderer font = mc.textRenderer;

		for (Waypoint w : ZeroVisualClient.get().waypoints().current()) {
			if (!w.visible || !w.dimension.equals(dimension)) continue;
			Vec3d target = new Vec3d(w.x + 0.5, w.y + 1.2, w.z + 0.5);
			Vec3d dir = target.subtract(cam);
			double distance = dir.length();
			if (distance < 0.5) continue;
			// far waypoints are drawn closer along the same direction so they stay inside the view distance
			double drawDistance = Math.min(distance, 48);
			Vec3d drawPos = cam.add(dir.multiply(drawDistance / distance));
			float s = (float) (0.0035 * Math.max(drawDistance, 4) * scale.get());

			Matrix4f m = new Matrix4f(view)
				.translate((float) (drawPos.x - cam.x), (float) (drawPos.y - cam.y), (float) (drawPos.z - cam.z))
				.rotate(context.camera().getRotation())
				.scale(s, -s, s);

			// diamond marker
			Matrix4f icon = new Matrix4f(m).translate(0, -10, 0).rotateZ((float) Math.toRadians(45));
			Render2D.shape(icon, -4.5f, -4.5f, 9, 9, 2, 0, 4, ColorUtil.fade(w.color, 0.5f), ColorUtil.fade(w.color, 0.5f),
				ColorUtil.fade(w.color, 0.5f), ColorUtil.fade(w.color, 0.5f));
			Render2D.shape(icon, -4.5f, -4.5f, 9, 9, 2, 0, 0, w.color, ColorUtil.brighten(w.color, 1.3f), w.color, ColorUtil.brighten(w.color, 1.3f));

			String label = showDistance.get() ? w.name + "  " + Math.round(distance) + "m" : w.name;
			Text text = Render2D.styled(label);
			font.draw(text, -font.getWidth(text) / 2f, 0, 0xFFFFFFFF, false, m, consumers, TextRenderer.TextLayerType.NORMAL,
				0x90000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		}
		consumers.draw();
	}
}
