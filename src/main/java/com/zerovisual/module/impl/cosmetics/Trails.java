package com.zerovisual.module.impl.cosmetics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
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

	public void render(WorldRenderContext context) {
		if (mc.player == null) return;
		long now = System.currentTimeMillis();
		float tickDelta = context.tickCounter().getTickDelta(true);
		Vec3d pos = mc.player.getLerpedPos(tickDelta);

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

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

		BufferBuilder strip = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
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
		if (count >= 2) BufferRenderer.drawWithGlobalProgram(strip.end());
		else strip.endNullable();

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
	}

	private record Point(Vec3d pos, long time) {
	}
}
