package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.StarShape;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** A small logo star that floats next to you, follows you smoothly and leaves a glowing trail. Only you see it. */
public class StarPet extends Module {
	private static final int TRAIL = 18;
	private static final long TRAIL_STEP_MS = 35;

	public final NumberSetting size = add(new NumberSetting("Size", 1, 0.5, 2, 0.1));
	public final BooleanSetting trail = add(new BooleanSetting("Trail", true));
	public final BooleanSetting firstPerson = add(new BooleanSetting("First person", true));

	private final Deque<Vec3d> history = new ArrayDeque<>();
	private Vec3d pos;
	private long lastFrame, lastTrail;

	public StarPet() {
		super("StarPet", "A little star companion that follows you (only you see it)", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		pos = null;
		history.clear();
	}

	public void render(WorldRenderContext context) {
		if (mc.player == null) return;
		long now = System.currentTimeMillis();
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float delta = context.tickCounter().getTickDelta(false);
		float time = now / 1000f;

		// behind the right shoulder, gently bobbing and circling a little
		double yaw = Math.toRadians(mc.player.getBodyYaw());
		double sideX = -Math.cos(yaw), sideZ = -Math.sin(yaw);
		double backX = Math.sin(yaw), backZ = -Math.cos(yaw);
		double wobble = Math.sin(time * 1.3) * 0.15;
		Vec3d target = mc.player.getLerpedPos(delta).add(
			sideX * (0.75 + wobble) + backX * 0.35,
			mc.player.getHeight() + 0.15 + Math.sin(time * 2.2) * 0.12,
			sideZ * (0.75 + wobble) + backZ * 0.35);
		if (pos == null || pos.squaredDistanceTo(target) > 400) pos = target;
		// smooth follow, frame rate independent
		pos = pos.lerp(target, 1 - Math.exp(-dt * 5));

		if (now - lastTrail >= TRAIL_STEP_MS) {
			lastTrail = now;
			history.addFirst(pos);
			while (history.size() > TRAIL) history.removeLast();
		}

		if (mc.options.getPerspective() == Perspective.FIRST_PERSON && !firstPerson.get()) return;
		Vec3d cam = context.camera().getPos();
		float s = 0.16f * size.getFloat();

		WorldRender.begin();
		if (trail.get()) {
			int i = 0;
			for (Iterator<Vec3d> it = history.iterator(); it.hasNext(); i++) {
				Vec3d p = it.next();
				if (i == 0) continue;
				float k = 1f - (float) i / TRAIL;
				int color = ColorUtil.lerp(StarShape.LEFT, StarShape.RIGHT, (float) i / TRAIL);
				WorldRender.glowDot(WorldRender.billboard(p, cam, context.camera().getRotation()), s * 0.18f * k,
					ColorUtil.withAlpha(color, 0.7f * k));
			}
		}
		Matrix4f m = WorldRender.billboard(pos, cam, context.camera().getRotation());
		WorldRender.glowDot(new Matrix4f(m).translate(0, 0, -0.01f), s * 0.35f, ColorUtil.withAlpha(StarShape.RIGHT, 0.35f));
		// a slow spin with a little "breathing"
		float pulse = 1f + 0.08f * (float) Math.sin(time * 4);
		StarShape.draw(new Matrix4f(m).rotateZ(time * 1.6f), s * pulse, 1f);
		WorldRender.end();
	}
}
