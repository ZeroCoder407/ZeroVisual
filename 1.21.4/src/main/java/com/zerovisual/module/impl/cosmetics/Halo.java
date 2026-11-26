package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.emote.EmoteAnchor;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** A glowing halo floating above the head (third person and other players). */
public class Halo extends Module {
	public final NumberSetting radius = add(new NumberSetting("Radius", 0.3, 0.15, 0.6, 0.01));
	public final BooleanSetting others = add(new BooleanSetting("Other players", false));

	public Halo() {
		super("Halo", "Glowing halo above your head", Category.COSMETICS);
	}

	public void render(WorldRenderContext context) {
		if (mc.world == null || mc.player == null) return;
		float tickDelta = context.tickCounter().getTickDelta(true);
		Vec3d cam = context.camera().getPos();
		float bob = (float) Math.sin(System.currentTimeMillis() / 400.0) * 0.03f;

		WorldRender.begin();
		for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
			boolean self = player == mc.player;
			if (self && !context.camera().isThirdPerson()) continue;
			if (!self && !others.get()) continue;
			if (player.isInvisible()) continue;
			Vec3d pos = player.getLerpedPos(tickDelta);
			float bodyYaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
			Matrix4f m = new Matrix4f()
				.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
				.mul(EmoteAnchor.head(player.getUuid(), bodyYaw, bodyYaw, player.getHeight(), 0.2f + bob))
				.rotateX((float) Math.toRadians(90));
			// a soft glow in the plane of the ring, then the ring itself as a lit tube
			int glow = com.zerovisual.render.ColorUtil.fade(Theme.accent(), 0.35f);
			float r = radius.getFloat();
			com.zerovisual.render.Render2D.shape(m, -r - 0.08f, -r - 0.08f, (r + 0.08f) * 2, (r + 0.08f) * 2, r + 0.08f, 0.16f, 0.07f, glow, glow, glow, glow);
			// solid: it hides its own far side
			com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
			WorldRender.quads(q -> com.zerovisual.fx.HaloRing.emit(q, m, r, 0.032f, Theme.accentLight(), Theme.accent()));
			com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
		}
		WorldRender.end();
	}
}
