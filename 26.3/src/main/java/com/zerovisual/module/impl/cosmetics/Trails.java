package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.ZvPipelines;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** A glowing ribbon following your player, fading out along its length. */
public class Trails extends Module {
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

	public void render(WorldContext context) {
		if (mc.player == null) return;
		long now = System.currentTimeMillis();
		float tickDelta = context.tickCounter().getGameTimeDeltaPartialTick(true);
		Vec3 pos = mc.player.getPosition(tickDelta);

		// sample at most every 15 ms and only when moving
		Point last = points.peekLast();
		if (last == null || (now - last.time > 15 && last.pos.distanceToSqr(pos) > 0.0004)) {
			points.addLast(new Point(pos, now));
		}
		long life = Math.round(length.get() * 1000);
		while (!points.isEmpty() && now - points.peekFirst().time > life) points.removeFirst();
		if (points.size() < 2) return;

		Vec3 cam = context.camera().position();
		boolean firstPerson = !context.camera().isDetached();
		float h = height.getFloat();
		float base = 0.15f;
		int accent = Theme.accent();
		int light = Theme.accentLight();
		Matrix4f m = new Matrix4f();

		// samples as (x, y, z, bottom color, top color); consecutive samples become one quad
		java.util.List<float[]> samples = new java.util.ArrayList<>();
		for (Point p : points) {
			float age = (now - p.time) / (float) life;
			// in first person the newest part would be inside the camera
			if (firstPerson && now - p.time < 200) continue;
			float alpha = (1f - age) * 0.6f;
			int color = ColorUtil.lerp(light, accent, age);
			samples.add(new float[]{(float) (p.pos.x - cam.x), (float) (p.pos.y - cam.y), (float) (p.pos.z - cam.z),
				ColorUtil.withAlpha(color, alpha * 0.3f), ColorUtil.withAlpha(color, alpha)});
		}
		if (samples.size() < 2) return;
		WorldRender.geometry(ZvPipelines.WORLD_COLOR_TYPE, buffer -> {
			for (int i = 0; i + 1 < samples.size(); i++) {
				float[] p0 = samples.get(i), p1 = samples.get(i + 1);
				buffer.addVertex(m, p0[0], p0[1] + base, p0[2]).setColor((int) p0[3]);
				buffer.addVertex(m, p0[0], p0[1] + base + h, p0[2]).setColor((int) p0[4]);
				buffer.addVertex(m, p1[0], p1[1] + base + h, p1[2]).setColor((int) p1[4]);
				buffer.addVertex(m, p1[0], p1[1] + base, p1[2]).setColor((int) p1[3]);
			}
		});

	}

	private record Point(Vec3 pos, long time) {
	}
}
