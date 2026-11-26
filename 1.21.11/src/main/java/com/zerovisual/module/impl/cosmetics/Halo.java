package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.emote.EmoteAnchor;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldContext;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** A glowing halo floating above the head (third person and other players). */
public class Halo extends Module {
	public final NumberSetting radius = add(new NumberSetting("Radius", 0.3, 0.15, 0.6, 0.01));
	public final BooleanSetting others = add(new BooleanSetting("Other players", false));

	public Halo() {
		super("Halo", "Glowing halo above your head", Category.COSMETICS);
	}

	public void render(WorldContext context) {
		if (mc.level == null || mc.player == null) return;
		float tickDelta = context.tickCounter().getGameTimeDeltaPartialTick(true);
		Vec3 cam = context.camera().position();
		float bob = (float) Math.sin(System.currentTimeMillis() / 400.0) * 0.03f;

		for (AbstractClientPlayer player : mc.level.players()) {
			boolean self = player == mc.player;
			if (self && !context.camera().isDetached()) continue;
			if (!self && !others.get()) continue;
			if (player.isInvisible()) continue;
			Vec3 pos = player.getPosition(tickDelta);
			float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
			Matrix4f m = new Matrix4f()
				.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
				.mul(EmoteAnchor.head(player.getUUID(), bodyYaw, bodyYaw, player.getBbHeight(), 0.2f + bob))
				.rotateX((float) Math.toRadians(90));
			// a soft glow in the plane of the ring, then the ring itself as a lit tube
			int glow = com.zerovisual.render.ColorUtil.fade(Theme.accent(), 0.35f);
			float r = radius.getFloat();
			WorldRender.shape(m, -r - 0.08f, -r - 0.08f, (r + 0.08f) * 2, (r + 0.08f) * 2, r + 0.08f, 0.16f, 0.07f, glow, glow, glow, glow);
			// solid and depth-writing: it hides its own far side
			WorldRender.geometry(com.zerovisual.render.ZvPipelines.WORLD_COLOR_SOLID_TYPE,
				b -> com.zerovisual.fx.HaloRing.emit((mat, x, y, z, c) -> b.addVertex(mat, x, y, z).setColor(c), m, r, 0.032f, Theme.accentLight(), Theme.accent()));
		}
	}
}
