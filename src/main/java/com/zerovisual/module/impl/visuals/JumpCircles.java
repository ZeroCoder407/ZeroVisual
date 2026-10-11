package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Expanding rings on the ground where you jump. */
public class JumpCircles extends Module {
	public final NumberSetting radius = add(new NumberSetting("Radius", 1.2, 0.5, 3, 0.1));
	public final NumberSetting duration = add(new NumberSetting("Duration", 1.2, 0.4, 3, 0.1));

	private final List<Circle> circles = new ArrayList<>();
	private boolean wasOnGround = true;

	public JumpCircles() {
		super("JumpCircles", "Круги на земле при прыжке", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		circles.clear();
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		boolean onGround = mc.player.isOnGround();
		if (wasOnGround && !onGround && mc.player.getVelocity().y > 0.1) {
			circles.add(new Circle(mc.player.getPos(), System.currentTimeMillis()));
		}
		wasOnGround = onGround;
	}

	public void render(WorldRenderContext context) {
		if (circles.isEmpty()) return;
		Vec3d cam = context.camera().getPos();
		long now = System.currentTimeMillis();
		float life = duration.getFloat() * 1000f;
		int accent = Theme.accent();
		int light = Theme.accentLight();

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();

		Iterator<Circle> it = circles.iterator();
		while (it.hasNext()) {
			Circle circle = it.next();
			float t = (now - circle.start) / life;
			if (t >= 1f) {
				it.remove();
				continue;
			}
			float eased = 1f - (1f - t) * (1f - t) * (1f - t);
			float r = 0.15f + radius.getFloat() * eased;
			float fade = 1f - t;

			Matrix4f m = new Matrix4f()
				.translate((float) (circle.pos.x - cam.x), (float) (circle.pos.y - cam.y) + 0.02f, (float) (circle.pos.z - cam.z))
				.rotateX((float) Math.toRadians(90));
			// glow + ring with a gradient across it
			Render2D.shape(m, -r, -r, r * 2, r * 2, r, 0, r * 0.25f,
				ColorUtil.withAlpha(accent, 0.25f * fade), ColorUtil.withAlpha(light, 0.25f * fade),
				ColorUtil.withAlpha(light, 0.25f * fade), ColorUtil.withAlpha(accent, 0.25f * fade));
			Render2D.shape(m, -r, -r, r * 2, r * 2, r, 0.06f, 0,
				ColorUtil.withAlpha(accent, fade), ColorUtil.withAlpha(light, fade),
				ColorUtil.withAlpha(light, fade), ColorUtil.withAlpha(accent, fade));
		}

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
	}

	private record Circle(Vec3d pos, long start) {
	}
}
