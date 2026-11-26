package com.zerovisual.module.impl.visuals;

import net.minecraft.client.renderer.GlStateManager;
import com.zerovisual.render.Mesh;
import com.zerovisual.emote.EmoteAnchor;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldRenderContext;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.MathHelper;
import com.zerovisual.util.Vec3d;
import org.joml.Matrix4f;

/** A translucent gradient cone above the head (visible in third person and on other players). */
public class ChinaHat extends Module implements com.zerovisual.ZeroVisualClient.WorldEffect {
	private static final int SEGMENTS = 48;

	public final NumberSetting radius = add(new NumberSetting("Radius", 0.6, 0.3, 1, 0.05));
	public final NumberSetting height = add(new NumberSetting("Height", 0.3, 0.1, 0.6, 0.05));
	public final BooleanSetting others = add(new BooleanSetting("Other players", false));

	public ChinaHat() {
		super("ChinaHat", "Cone hat above your head (third-person view)", Category.COSMETICS);
	}

	@Override
	public void render(WorldRenderContext context) {
		if (mc.theWorld == null || mc.thePlayer == null) return;
		float tickDelta = context.tickCounter().getTickDelta(true);
		Vec3d cam = context.camera().getPos();

		GlStateManager.enableBlend();
		Mesh.defaultBlendFunc();
		GlStateManager.enableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.disableCull();
		for (net.minecraft.entity.player.EntityPlayer entry : mc.theWorld.playerEntities) {
			if (!(entry instanceof AbstractClientPlayer)) continue;
			AbstractClientPlayer player = (AbstractClientPlayer) entry;
			boolean self = player == mc.thePlayer;
			if (self && !context.camera().isThirdPerson()) continue;
			if (!self && !others.get()) continue;
			if (player.isInvisible()) continue;
			drawHat(player, tickDelta, cam);
		}

		GlStateManager.enableCull();
		GlStateManager.depthMask(true);
		GlStateManager.disableBlend();
	}

	private void drawHat(AbstractClientPlayer player, float tickDelta, Vec3d cam) {
		Vec3d pos = Vec3d.lerpedPos(player, tickDelta);
		float yaw = com.zerovisual.util.Mth.lerpAngle(tickDelta, player.prevRotationYawHead, player.rotationYawHead);
		float bodyYaw = com.zerovisual.util.Mth.lerpAngle(tickDelta, player.prevRenderYawOffset, player.renderYawOffset);
		Matrix4f m = new Matrix4f()
			.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
			.mul(EmoteAnchor.head(player.getUniqueID(), bodyYaw, yaw, player.height, 0.03f));

		float r = radius.getFloat();
		float h = height.getFloat();
		int accent = Theme.accent();
		int light = Theme.accentLight();

		Mesh fan = Mesh.begin(Mesh.TRIANGLE_FAN);
		fan.vertex(m, 0, h, 0).color(ColorUtil.withAlpha(light, 0.75f));
		for (int i = 0; i <= SEGMENTS; i++) {
			double a = Math.PI * 2 * i / SEGMENTS;
			// two-tone gradient around the rim
			float mix = (float) (0.5 + 0.5 * Math.sin(a));
			fan.vertex(m, (float) (Math.cos(a) * r), 0, (float) (Math.sin(a) * r)).color(ColorUtil.withAlpha(ColorUtil.lerp(accent, light, mix), 0.35f));
		}
		fan.draw();

		Mesh rim = Mesh.begin(Mesh.DEBUG_LINE_STRIP);
		for (int i = 0; i <= SEGMENTS; i++) {
			double a = Math.PI * 2 * i / SEGMENTS;
			float mix = (float) (0.5 + 0.5 * Math.sin(a));
			rim.vertex(m, (float) (Math.cos(a) * r), 0, (float) (Math.sin(a) * r)).color(ColorUtil.withAlpha(ColorUtil.lerp(accent, light, mix), 0.9f));
		}
		rim.draw();
	}
}
