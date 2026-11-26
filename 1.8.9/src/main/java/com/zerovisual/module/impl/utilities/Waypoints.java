package com.zerovisual.module.impl.utilities;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.WaypointsScreen;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.WorldRenderContext;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.KeySetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.util.Lang;
import com.zerovisual.util.Vec3d;
import com.zerovisual.waypoint.WaypointManager;
import com.zerovisual.waypoint.WaypointManager.Waypoint;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.joml.Matrix4f;
import org.lwjgl.input.Keyboard;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Your own waypoints, drawn in the world with name and distance (also through walls, like a minimap).
 * J opens the waypoint list. A waypoint is created where you die.
 */
public class Waypoints extends Module implements ZeroVisualClient.WorldEffect {
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
		openKey = new KeyBinding("key.zerovisual.waypoints", Keyboard.KEY_J, "category.zerovisual");
		ClientRegistry.registerKeyBinding(openKey);
	}

	@Override
	public void onTick() {
		if (mc.thePlayer == null) return;
		while (openKey != null && openKey.isPressed()) {
			if (mc.currentScreen == null) mc.displayGuiScreen(new WaypointsScreen(null));
		}
		boolean dead = mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0;
		if (dead && !wasDead && deathPoint.get()) {
			String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
			BlockPos at = new BlockPos(mc.thePlayer);
			Waypoint waypoint = ZeroVisualClient.get().waypoints().add(Lang.get("zerovisual.waypoints.death", time), at.getX(), at.getY(), at.getZ());
			waypoint.color = 0xFFF43F5E;
			ZeroVisualClient.get().waypoints().save();
		}
		wasDead = dead;
	}

	/** Drawn last in the world without the depth test, so markers and labels show through walls. */
	@Override
	public void render(WorldRenderContext context) {
		if (mc.thePlayer == null) return;
		String dimension = WaypointManager.currentDimension();
		Vec3d cam = context.camera().getPos();
		boolean any = false;
		for (Waypoint w : ZeroVisualClient.get().waypoints().current()) {
			if (!w.visible || !w.dimension.equals(dimension)) continue;
			if (!any) {
				WorldRender.begin();
				GlStateManager.disableDepth();
				any = true;
			}
			// the camera sits at the eyes, the origin of the world drawing at the feet
			Vec3d eye = cam.add(0, mc.getRenderViewEntity().getEyeHeight(), 0);
			Vec3d target = new Vec3d(w.x + 0.5, w.y + 1.2, w.z + 0.5);
			Vec3d dir = target.subtract(eye);
			double distance = dir.length();
			if (distance < 0.5) continue;
			// far waypoints are drawn closer along the same direction so they stay inside the view distance
			double drawDistance = Math.min(distance, 48);
			Vec3d drawPos = eye.add(dir.multiply(drawDistance / distance));
			float s = (float) (0.0035 * Math.max(drawDistance, 4) * scale.get());

			Matrix4f m = WorldRender.billboard(drawPos, cam, context.camera().getRotation()).scale(s, -s, s);

			// diamond marker
			Matrix4f icon = new Matrix4f(m).translate(0, -10, 0).rotateZ((float) Math.toRadians(45));
			WorldRender.shape(icon, -4.5f, -4.5f, 9, 9, 2, 0, 4, ColorUtil.fade(w.color, 0.5f), ColorUtil.fade(w.color, 0.5f),
				ColorUtil.fade(w.color, 0.5f), ColorUtil.fade(w.color, 0.5f));
			WorldRender.shape(icon, -4.5f, -4.5f, 9, 9, 2, 0, 0, w.color, ColorUtil.brighten(w.color, 1.3f), w.color, ColorUtil.brighten(w.color, 1.3f));

			String label = showDistance.get() ? w.name + "  " + Math.round(distance) + "m" : w.name;
			float width = mc.fontRendererObj.getStringWidth(label);
			WorldRender.text(m, label, -width / 2f, 0, 0xFFFFFFFF, 0x90000000, true, false);
		}
		if (any) {
			GlStateManager.enableDepth();
			WorldRender.end();
		}
	}
}
