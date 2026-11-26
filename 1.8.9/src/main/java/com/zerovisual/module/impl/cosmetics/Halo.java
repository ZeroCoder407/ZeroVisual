package com.zerovisual.module.impl.cosmetics;

import net.minecraft.client.renderer.GlStateManager;

import com.zerovisual.emote.EmoteAnchor;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldRenderContext;
import net.minecraft.client.entity.AbstractClientPlayer;
import com.zerovisual.util.Vec3d;
import org.joml.Matrix4f;

/** A glowing halo floating above the head (third person and other players). */
public class Halo extends Module implements com.zerovisual.ZeroVisualClient.WorldEffect {
	public final NumberSetting radius = add(new NumberSetting("Radius", 0.3, 0.15, 0.6, 0.01));
	public final BooleanSetting others = add(new BooleanSetting("Other players", false));

	public Halo() {
		super("Halo", "Glowing halo above your head", Category.COSMETICS);
	}

	@Override
	public void render(WorldRenderContext context) {
		if (mc.theWorld == null || mc.thePlayer == null) return;
		float tickDelta = context.tickCounter().getTickDelta(true);
		Vec3d cam = context.camera().getPos();
		float bob = (float) Math.sin(System.currentTimeMillis() / 400.0) * 0.03f;

		WorldRender.begin();
		for (net.minecraft.entity.player.EntityPlayer entry : mc.theWorld.playerEntities) {
			if (!(entry instanceof AbstractClientPlayer)) continue;
			AbstractClientPlayer player = (AbstractClientPlayer) entry;
			boolean self = player == mc.thePlayer;
			if (self && !context.camera().isThirdPerson()) continue;
			if (!self && !others.get()) continue;
			if (player.isInvisible()) continue;
			Vec3d pos = Vec3d.lerpedPos(player, tickDelta);
			float bodyYaw = com.zerovisual.util.Mth.lerpAngle(tickDelta, player.prevRenderYawOffset, player.renderYawOffset);
			Matrix4f m = new Matrix4f()
				.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
				.mul(EmoteAnchor.head(player.getUniqueID(), bodyYaw, bodyYaw, player.height, 0.2f + bob))
				.rotateX((float) Math.toRadians(90));
			// a soft glow in the plane of the ring, then the ring itself as a lit tube
			int glow = com.zerovisual.render.ColorUtil.fade(Theme.accent(), 0.35f);
			float r = radius.getFloat();
			WorldRender.shape(m, -r - 0.08f, -r - 0.08f, (r + 0.08f) * 2, (r + 0.08f) * 2, r + 0.08f, 0.16f, 0.07f, glow, glow, glow, glow);
			// solid: it hides its own far side
			GlStateManager.depthMask(true);
			WorldRender.quads(q -> com.zerovisual.fx.HaloRing.emit(q, m, r, 0.032f, Theme.accentLight(), Theme.accent()));
			GlStateManager.depthMask(false);
		}
		WorldRender.end();
	}

}
