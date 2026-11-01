package com.zerovisual.module.impl.cosmetics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.Render2D;
import com.zerovisual.render.StarShape;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.render.WorldContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Totem of undying effects: your own totem shows the logo star flying into the screen and bursting
 * instead of the floating totem item; players who pop a totem get a short "-N" counter above their head.
 */
public class TotemPop extends Module {
	private static final float GROW_END = 0.45f;
	private static final float BURST_AT = 0.75f;
	private static final float END = 1.6f;
	private static final long LABEL_MS = 3000;
	private static final int SPARKS = 22;

	public final BooleanSetting animation = add(new BooleanSetting("Animation", true));
	public final BooleanSetting counter = add(new BooleanSetting("Counter", true));

	private final Map<Integer, Integer> pops = new HashMap<>();
	private final Map<Integer, Long> lastPop = new HashMap<>();
	private final float[] sparkAngle = new float[SPARKS];
	private final float[] sparkSpeed = new float[SPARKS];
	private final Random random = new Random();
	private long animationStart = -1;
	private ClientLevel lastWorld;

	public TotemPop() {
		super("TotemPop", "Logo star animation for your totem and a pop counter above players", Category.COSMETICS);
		setEnabled(true);
	}

	/** Replaces the vanilla floating totem when true. */
	public boolean replacesFloatingItem() {
		return isEnabled() && animation.get();
	}

	/** Entity status 35: someone used a totem of undying. */
	public void onPop(Entity entity) {
		if (!isEnabled()) return;
		pops.merge(entity.getId(), 1, Integer::sum);
		lastPop.put(entity.getId(), System.currentTimeMillis());
		if (entity == mc.player && animation.get()) startAnimation();
	}

	public void startAnimation() {
		animationStart = System.currentTimeMillis();
		for (int i = 0; i < SPARKS; i++) {
			sparkAngle[i] = (float) (i * 2 * Math.PI / SPARKS + random.nextFloat() * 0.3f);
			sparkSpeed[i] = 0.6f + random.nextFloat() * 0.8f;
		}
	}

	@Override
	public void onTick() {
		if (mc.level != lastWorld) {
			lastWorld = mc.level;
			pops.clear();
			lastPop.clear();
		}
		if (mc.level == null) return;
		// the counter starts over when the player dies or leaves
		pops.keySet().removeIf(id -> {
			Entity e = mc.level.getEntity(id);
			return e == null || !e.isAlive();
		});
	}

	// ---- HUD animation ----

	public void renderHud(GuiGraphics ctx) {
		if (animationStart < 0) return;
		float s = (System.currentTimeMillis() - animationStart) / 1000f;
		if (s >= END) {
			animationStart = -1;
			return;
		}
		float cx = ctx.guiWidth() / 2f, cy = ctx.guiHeight() / 2f;
		float size = Math.min(ctx.guiWidth(), ctx.guiHeight()) * 0.2f;
		drawAnimation(ctx, s, cx, cy, size);
	}

	private void drawAnimation(GuiGraphics ctx, float s, float cx, float cy, float size) {

		if (s < BURST_AT) {
			// flies in from far away: grows quickly while spinning down
			float grow = easeOut(Math.min(1f, s / GROW_END));
			float pulse = s > GROW_END ? 1f + 0.06f * (float) Math.sin((s - GROW_END) * 30) : 1f;
			float angle = 540f * (1f - grow);
			ctx.pose().pushMatrix();
			ctx.pose().translate(cx, cy).rotate((float) Math.toRadians(angle));
			StarShape.draw(ctx, size * grow * pulse, Math.min(1f, s / 0.12f));
			ctx.pose().popMatrix();
			return;
		}

		float p = (s - BURST_AT) / (END - BURST_AT);
		float alpha = 1f - p;
		// a soft flash right at the burst
		float flash = 1f - Math.min(1f, p * 4f);
		if (flash > 0) Render2D.circle(ctx, cx, cy, size * (0.6f + p * 2f), ColorUtil.withAlpha(StarShape.RIGHT, 0.35f * flash));
		for (int i = 0; i < 5; i++) {
			double dir = StarShape.rayAngle(i);
			float d = size * 1.4f * easeOut(p);
			ctx.pose().pushMatrix();
			ctx.pose().translate(cx + (float) Math.cos(dir) * d, cy + (float) Math.sin(dir) * d)
				.rotate((float) Math.toRadians((i % 2 == 0 ? 1 : -1) * 160f * p))
				.scale(1f - 0.5f * p);
			StarShape.drawRay(ctx, size, i, alpha);
			ctx.pose().popMatrix();
		}
		for (int i = 0; i < SPARKS; i++) {
			float d = size * (0.3f + 2.2f * sparkSpeed[i] * easeOut(p));
			float x = cx + (float) Math.cos(sparkAngle[i]) * d, y = cy + (float) Math.sin(sparkAngle[i]) * d + p * p * size * 0.6f;
			int color = ColorUtil.lerp(StarShape.LEFT, StarShape.RIGHT, (float) i / SPARKS);
			Render2D.circle(ctx, x, y, 3f * (1f - p * 0.6f), ColorUtil.withAlpha(color, alpha));
		}
	}

	// ---- Counter above players ----

	public void renderWorld(WorldContext context) {
		if (!counter.get() || lastPop.isEmpty() || mc.level == null) return;
		long now = System.currentTimeMillis();
		lastPop.values().removeIf(t -> now - t > LABEL_MS);
		Vec3 cam = context.camera().position();
		float delta = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Font font = mc.font;

		for (Map.Entry<Integer, Long> entry : lastPop.entrySet()) {
			Entity entity = mc.level.getEntity(entry.getKey());
			if (!(entity instanceof Player) || entity == mc.getCameraEntity()) continue;
			float t = (now - entry.getValue()) / (float) LABEL_MS;
			float alpha = t < 0.8f ? 1f : 1f - (t - 0.8f) / 0.2f;
			// pops up a little at the start
			float rise = 0.25f * easeOut(Math.min(1f, t * 4));
			Vec3 pos = entity.getPosition(delta).add(0, entity.getBbHeight() + 0.75 + rise, 0);
			float s = 0.03f;
			Matrix4f m = new Matrix4f()
				.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
				.rotate(context.camera().rotation())
				.scale(s, -s, s);
			Component text = Render2D.styled("✦ -" + pops.getOrDefault(entry.getKey(), 1));
			int color = ColorUtil.withAlpha(ColorUtil.lerp(StarShape.LEFT, StarShape.RIGHT, 0.5f), Math.max(0.05f, alpha));
			WorldRender.text(m, text, -font.width(text) / 2f, 0, color, ColorUtil.withAlpha(0xFF0C0C12, 0.55f * alpha), false, false);
		}
	}

	@Override
	protected void onDisable() {
		animationStart = -1;
		pops.clear();
		lastPop.clear();
	}

	private static float easeOut(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}
}
