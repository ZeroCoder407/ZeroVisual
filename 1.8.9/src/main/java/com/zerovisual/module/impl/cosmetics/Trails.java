package com.zerovisual.module.impl.cosmetics;

import net.minecraft.client.renderer.GlStateManager;
import com.zerovisual.render.Mesh;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldRenderContext;
import com.zerovisual.util.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** A glowing ribbon following your player, fading out along its length. */
public class Trails extends Module implements com.zerovisual.ZeroVisualClient.WorldEffect {
	public final NumberSetting length = add(new NumberSetting("Length", 1.2, 0.3, 3, 0.1));
	public final NumberSetting height = add(new NumberSetting("Height", 0.5, 0.1, 1.5, 0.05));

	private final Deque<Point> points = new ArrayDeque<>();

	public Trails() {
		super("Trails", "Glowing trail behind the player", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		points.clear();
	}

	@Override
	public void render(WorldRenderContext context) {
		if (mc.thePlayer == null) return;
		long now = System.currentTimeMillis();
		float tickDelta = context.tickCounter().getTickDelta(true);
		Vec3d pos = Vec3d.lerpedPos(mc.thePlayer, tickDelta);

		// sample at most every 15 ms and only when moving
		Point last = points.peekLast();
		if (last == null || (now - last.time > 15 && last.pos.squaredDistanceTo(pos) > 0.0004)) {
			points.addLast(new Point(pos, now));
		}
		long life = Math.round(length.get() * 1000);
		while (!points.isEmpty() && now - points.peekFirst().time > life) points.removeFirst();
		if (points.size() < 2) return;

		Vec3d cam = context.camera().getPos();
		boolean firstPerson = !context.camera().isThirdPerson();
		float h = height.getFloat();
		float base = 0.15f;
		int accent = Theme.accent();
		int light = Theme.accentLight();
		Matrix4f m = new Matrix4f();

		GlStateManager.enableBlend();
		Mesh.defaultBlendFunc();
		GlStateManager.enableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.disableCull();
		Mesh strip = Mesh.begin(Mesh.TRIANGLE_STRIP);
		int count = 0;
		Iterator<Point> it = points.iterator();
		while (it.hasNext()) {
			Point p = it.next();
			float age = (now - p.time) / (float) life;
			// in first person the newest part would be inside the camera
			if (firstPerson && now - p.time < 200) continue;
			float alpha = (1f - age) * 0.6f;
			int color = ColorUtil.lerp(light, accent, age);
			float x = (float) (p.pos.x - cam.x), y = (float) (p.pos.y - cam.y), z = (float) (p.pos.z - cam.z);
			strip.vertex(m, x, y + base, z).color(ColorUtil.withAlpha(color, alpha * 0.3f));
			strip.vertex(m, x, y + base + h, z).color(ColorUtil.withAlpha(color, alpha));
			count++;
		}
		if (count >= 2) strip.draw();
		else strip.discard();

		GlStateManager.enableCull();
		GlStateManager.depthMask(true);
		GlStateManager.disableBlend();
	}

	private static final class Point {
		final Vec3d pos;
		final long time;

		Point(Vec3d pos, long time) {
			this.pos = pos;
			this.time = time;
		}

		Vec3d pos() {
			return pos;
		}

		long time() {
			return time;
		}

	}
}
