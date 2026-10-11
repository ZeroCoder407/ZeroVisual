package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.util.TargetTracker;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Marker on the entity you are fighting (the last one you hit).
 * Depth-tested: it does not show through walls.
 */
public class TargetEsp extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Ring", "Ring", "Marker"));
	public final NumberSetting keepTime = add(new NumberSetting("Keep time", 3, 1, 10, 0.5));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1, 0.3, 3, 0.1));

	private final Anim visible = new Anim(10f, 0f);
	private LivingEntity shown;

	public TargetEsp() {
		super("TargetESP", "Указатель на цели, по которой вы бьёте (не сквозь стены)", Category.VISUALS);
	}

	public void render(WorldRenderContext context) {
		LivingEntity target = TargetTracker.get(Math.round(keepTime.get() * 1000));
		if (target != null) shown = target;
		float v = visible.update(target != null);
		if (v <= 0.01f || shown == null) {
			if (v <= 0.01f) shown = null;
			return;
		}

		float tickDelta = context.tickCounter().getTickDelta(true);
		Vec3d cam = context.camera().getPos();
		Vec3d pos = shown.getLerpedPos(tickDelta);
		float time = (System.currentTimeMillis() % 100_000L) / 1000f * speed.getFloat();
		int accent = Theme.accent();
		int light = Theme.accentLight();

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();

		if (style.is("Ring")) {
			float radius = shown.getWidth() * 0.75f + 0.15f;
			// the ring travels up and down the body
			float wave = (float) (Math.sin(time * 2.2) * 0.5 + 0.5);
			float y = (float) (pos.y - cam.y) + 0.05f + wave * (shown.getHeight() - 0.1f);
			Matrix4f m = new Matrix4f()
				.translate((float) (pos.x - cam.x), y, (float) (pos.z - cam.z))
				.rotateX((float) Math.toRadians(90));
			int a = ColorUtil.withAlpha(accent, v), b = ColorUtil.withAlpha(light, v);
			int ga = ColorUtil.withAlpha(accent, 0.3f * v), gb = ColorUtil.withAlpha(light, 0.3f * v);
			Render2D.shape(m, -radius, -radius, radius * 2, radius * 2, radius, 0, 0.12f, ga, gb, gb, ga);
			Render2D.shape(m, -radius, -radius, radius * 2, radius * 2, radius, 0.035f, 0, a, b, b, a);
		} else {
			float size = Math.max(0.35f, shown.getHeight() * 0.3f) * (0.9f + 0.1f * v);
			Matrix4f m = new Matrix4f()
				.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y) + shown.getHeight() / 2f, (float) (pos.z - cam.z))
				.rotate(context.camera().getRotation())
				.rotateZ(time * 1.5f);
			int a = ColorUtil.withAlpha(accent, v), b = ColorUtil.withAlpha(light, v);
			Render2D.shape(m, -size, -size, size * 2, size * 2, size * 0.35f, 0, size * 0.25f,
				ColorUtil.withAlpha(accent, 0.25f * v), ColorUtil.withAlpha(light, 0.25f * v),
				ColorUtil.withAlpha(accent, 0.25f * v), ColorUtil.withAlpha(light, 0.25f * v));
			Render2D.shape(m, -size, -size, size * 2, size * 2, size * 0.35f, size * 0.12f, 0, a, b, a, b);
		}

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
	}
}
