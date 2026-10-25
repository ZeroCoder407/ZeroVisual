package com.zerovisual.module.impl.cosmetics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Translucent gradient wings on your back that slowly flap (third person and other players). */
public class Wings extends Module {
	/** Outline of the right wing (x outwards, y up), root at (0, 0). Alternating radii give the feather tips. */
	private static final float[][] SHAPE = {
		{0.10f, 0.22f}, {0.32f, 0.42f}, {0.55f, 0.55f}, {0.78f, 0.58f}, {0.95f, 0.50f}, {1.00f, 0.32f},
		{0.86f, 0.20f}, {0.98f, 0.05f}, {0.80f, -0.04f}, {0.88f, -0.20f}, {0.66f, -0.24f}, {0.70f, -0.42f},
		{0.48f, -0.40f}, {0.46f, -0.58f}, {0.28f, -0.48f}, {0.12f, -0.30f}
	};

	public final NumberSetting size = add(new NumberSetting("Size", 0.7, 0.4, 1.2, 0.05));
	public final NumberSetting flapSpeed = add(new NumberSetting("Flap speed", 1, 0.2, 3, 0.1));
	public final BooleanSetting others = add(new BooleanSetting("Other players", false));

	public Wings() {
		super("Wings", "Wings on your back (third-person view)", Category.COSMETICS);
	}

	public void render(WorldRenderContext context) {
		if (mc.world == null || mc.player == null) return;
		float tickDelta = context.tickCounter().getTickDelta(true);
		Vec3d cam = context.camera().getPos();
		float time = (System.currentTimeMillis() % 100_000L) / 1000f;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

		for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
			boolean self = player == mc.player;
			if (self && !context.camera().isThirdPerson()) continue;
			if (!self && !others.get()) continue;
			if (player.isInvisible() || player.isGliding()) continue;

			Vec3d pos = player.getLerpedPos(tickDelta);
			float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
			// faster flapping while moving
			double speed = player.getVelocity().horizontalLength();
			float flap = (float) Math.sin(time * flapSpeed.getFloat() * (2.2 + speed * 8)) * 18f + 22f;
			float back = player.isInSneakingPose() ? 0.35f : 0.18f;
			float height = player.getHeight() * (player.isInSneakingPose() ? 0.7f : 0.74f);

			Matrix4f base = new Matrix4f()
				.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y) + height, (float) (pos.z - cam.z))
				.rotateY((float) Math.toRadians(-bodyYaw))
				.translate(0, 0, -back);
			drawWing(base, 1, flap);
			drawWing(base, -1, flap);
		}

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
	}

	/** @param side 1 = right wing, -1 = left wing */
	private void drawWing(Matrix4f base, int side, float flapDegrees) {
		float s = size.getFloat();
		// hinge near the spine, the wing tips swing backwards (-z, away from the body) around the vertical axis
		Matrix4f m = new Matrix4f(base)
			.translate(side * 0.08f, 0, 0)
			.rotateY((float) Math.toRadians(side * flapDegrees))
			.scale(side * s, s, s);

		int accent = Theme.accent();
		int light = Theme.accentLight();

		BufferBuilder fan = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		fan.vertex(m, 0, 0, 0).color(ColorUtil.withAlpha(accent, 0.85f));
		for (float[] p : SHAPE) {
			float reach = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1]);
			fan.vertex(m, p[0], p[1], 0).color(ColorUtil.withAlpha(ColorUtil.lerp(accent, light, reach), 0.25f + 0.2f * (1 - reach)));
		}
		BufferRenderer.drawWithGlobalProgram(fan.end());

		BufferBuilder outline = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
		outline.vertex(m, 0, 0, 0).color(ColorUtil.withAlpha(light, 0.9f));
		for (float[] p : SHAPE) outline.vertex(m, p[0], p[1], 0).color(ColorUtil.withAlpha(light, 0.9f));
		outline.vertex(m, 0, 0, 0).color(ColorUtil.withAlpha(light, 0.9f));
		BufferRenderer.drawWithGlobalProgram(outline.end());

		// a few feather lines from the root
		BufferBuilder feathers = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
		for (int i = 1; i < SHAPE.length; i += 3) {
			feathers.vertex(m, 0, 0, 0).color(ColorUtil.withAlpha(light, 0.35f));
			feathers.vertex(m, SHAPE[i][0] * 0.85f, SHAPE[i][1] * 0.85f, 0).color(ColorUtil.withAlpha(light, 0.05f));
		}
		BufferRenderer.drawWithGlobalProgram(feathers.end());
	}
}
