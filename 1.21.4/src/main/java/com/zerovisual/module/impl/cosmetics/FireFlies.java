package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Glowing fireflies drifting and twinkling around you. */
public class FireFlies extends Module {
	public final NumberSetting count = add(new NumberSetting("Count", 30, 5, 100, 1));
	public final NumberSetting radius = add(new NumberSetting("Radius", 6, 2, 16, 0.5));
	public final NumberSetting size = add(new NumberSetting("Size", 0.04, 0.02, 0.12, 0.005));

	private final List<Fly> flies = new ArrayList<>();
	private final Random random = new Random();
	private long lastFrame;

	public FireFlies() {
		super("FireFlies", "Glowing fireflies flying around you", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		flies.clear();
	}

	public void render(WorldRenderContext context) {
		if (mc.player == null) return;
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		float time = (System.currentTimeMillis() % 1_000_000L) / 1000f;

		Vec3d center = mc.player.getLerpedPos(context.tickCounter().getTickDelta(true)).add(0, 1, 0);
		float r = radius.getFloat();
		while (flies.size() < count.getInt()) flies.add(new Fly(randomAround(center, r)));
		while (flies.size() > count.getInt()) flies.removeLast();

		for (Fly fly : flies) {
			// steer towards a slowly changing wander target
			if (fly.target == null || fly.pos.squaredDistanceTo(fly.target) < 0.2 || random.nextFloat() < dt * 0.3f) {
				fly.target = randomAround(center, r);
			}
			Vec3d toTarget = fly.target.subtract(fly.pos).normalize().multiply(0.6);
			fly.velocity = fly.velocity.multiply(Math.pow(0.4, dt)).add(toTarget.multiply(dt * 1.5));
			fly.pos = fly.pos.add(fly.velocity.multiply(dt));
			if (fly.pos.squaredDistanceTo(center) > r * r * 2.5) fly.pos = randomAround(center, r);
		}

		Vec3d cam = context.camera().getPos();
		Quaternionf rotation = context.camera().getRotation();
		int accent = Theme.accent();
		int light = Theme.accentLight();
		float s = size.getFloat();

		WorldRender.begin();
		for (Fly fly : flies) {
			float twinkle = (float) (0.55 + 0.45 * Math.sin(time * fly.speed + fly.phase));
			int color = ColorUtil.withAlpha(ColorUtil.lerp(accent, light, fly.hue), twinkle);
			WorldRender.glowDot(WorldRender.billboard(fly.pos, cam, rotation), s * (0.8f + 0.4f * twinkle), color);
		}
		WorldRender.end();
	}

	private Vec3d randomAround(Vec3d center, float r) {
		return center.add((random.nextDouble() * 2 - 1) * r, (random.nextDouble() * 2 - 1) * r * 0.5, (random.nextDouble() * 2 - 1) * r);
	}

	private final class Fly {
		Vec3d pos;
		Vec3d velocity = Vec3d.ZERO;
		Vec3d target;
		final float phase = random.nextFloat() * 6.28f;
		final float speed = 1.5f + random.nextFloat() * 2.5f;
		final float hue = random.nextFloat();

		Fly(Vec3d pos) {
			this.pos = pos;
		}
	}
}
