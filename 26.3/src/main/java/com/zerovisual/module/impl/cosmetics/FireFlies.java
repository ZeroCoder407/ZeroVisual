package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldContext;
import net.minecraft.world.phys.Vec3;
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

	public void render(WorldContext context) {
		if (mc.player == null) return;
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		float time = (System.currentTimeMillis() % 1_000_000L) / 1000f;

		Vec3 center = mc.player.getPosition(context.tickCounter().getGameTimeDeltaPartialTick(true)).add(0, 1, 0);
		float r = radius.getFloat();
		while (flies.size() < count.getInt()) flies.add(new Fly(randomAround(center, r)));
		while (flies.size() > count.getInt()) flies.removeLast();

		for (Fly fly : flies) {
			// steer towards a slowly changing wander target
			if (fly.target == null || fly.pos.distanceToSqr(fly.target) < 0.2 || random.nextFloat() < dt * 0.3f) {
				fly.target = randomAround(center, r);
			}
			Vec3 toTarget = fly.target.subtract(fly.pos).normalize().scale(0.6);
			fly.velocity = fly.velocity.scale(Math.pow(0.4, dt)).add(toTarget.scale(dt * 1.5));
			fly.pos = fly.pos.add(fly.velocity.scale(dt));
			if (fly.pos.distanceToSqr(center) > r * r * 2.5) fly.pos = randomAround(center, r);
		}

		Vec3 cam = context.camera().position();
		Quaternionf rotation = context.camera().rotation();
		int accent = Theme.accent();
		int light = Theme.accentLight();
		float s = size.getFloat();

		for (Fly fly : flies) {
			float twinkle = (float) (0.55 + 0.45 * Math.sin(time * fly.speed + fly.phase));
			int color = ColorUtil.withAlpha(ColorUtil.lerp(accent, light, fly.hue), twinkle);
			WorldRender.glowDot(WorldRender.billboard(fly.pos, cam, rotation), s * (0.8f + 0.4f * twinkle), color);
		}
	}

	private Vec3 randomAround(Vec3 center, float r) {
		return center.add((random.nextDouble() * 2 - 1) * r, (random.nextDouble() * 2 - 1) * r * 0.5, (random.nextDouble() * 2 - 1) * r);
	}

	private final class Fly {
		Vec3 pos;
		Vec3 velocity = Vec3.ZERO;
		Vec3 target;
		final float phase = random.nextFloat() * 6.28f;
		final float speed = 1.5f + random.nextFloat() * 2.5f;
		final float hue = random.nextFloat();

		Fly(Vec3 pos) {
			this.pos = pos;
		}
	}
}
