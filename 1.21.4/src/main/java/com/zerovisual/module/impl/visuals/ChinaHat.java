package com.zerovisual.module.impl.visuals;

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

/** A translucent gradient cone above the head (visible in third person and on other players). */
public class ChinaHat extends Module {
	private static final int SEGMENTS = 48;

	public final NumberSetting radius = add(new NumberSetting("Radius", 0.6, 0.3, 1, 0.05));
	public final NumberSetting height = add(new NumberSetting("Height", 0.3, 0.1, 0.6, 0.05));
	public final BooleanSetting others = add(new BooleanSetting("Other players", false));

	public ChinaHat() {
		super("ChinaHat", "Cone hat above your head (third-person view)", Category.COSMETICS);
	}

	public void render(WorldRenderContext context) {
		if (mc.world == null || mc.player == null) return;
		float tickDelta = context.tickCounter().getTickDelta(true);
		Vec3d cam = context.camera().getPos();

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
			if (player.isInvisible()) continue;
			drawHat(player, tickDelta, cam);
		}

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
	}

	private void drawHat(AbstractClientPlayerEntity player, float tickDelta, Vec3d cam) {
		Vec3d pos = player.getLerpedPos(tickDelta);
		float yaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
		Matrix4f m = new Matrix4f()
			.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y) + player.getHeight() + 0.03f, (float) (pos.z - cam.z))
			.rotateY((float) Math.toRadians(-yaw));

		float r = radius.getFloat();
		float h = height.getFloat();
		int accent = Theme.accent();
		int light = Theme.accentLight();

		BufferBuilder fan = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		fan.vertex(m, 0, h, 0).color(ColorUtil.withAlpha(light, 0.75f));
		for (int i = 0; i <= SEGMENTS; i++) {
			double a = Math.PI * 2 * i / SEGMENTS;
			// two-tone gradient around the rim
			float mix = (float) (0.5 + 0.5 * Math.sin(a));
			fan.vertex(m, (float) (Math.cos(a) * r), 0, (float) (Math.sin(a) * r)).color(ColorUtil.withAlpha(ColorUtil.lerp(accent, light, mix), 0.35f));
		}
		BufferRenderer.drawWithGlobalProgram(fan.end());

		BufferBuilder rim = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
		for (int i = 0; i <= SEGMENTS; i++) {
			double a = Math.PI * 2 * i / SEGMENTS;
			float mix = (float) (0.5 + 0.5 * Math.sin(a));
			rim.vertex(m, (float) (Math.cos(a) * r), 0, (float) (Math.sin(a) * r)).color(ColorUtil.withAlpha(ColorUtil.lerp(accent, light, mix), 0.9f));
		}
		BufferRenderer.drawWithGlobalProgram(rim.end());
	}
}
