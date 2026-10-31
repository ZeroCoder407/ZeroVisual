package com.zerovisual.module.impl.visuals;

import com.zerovisual.emote.EmoteAnchor;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.ZvPipelines;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldContext;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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

	public void render(WorldContext context) {
		if (mc.level == null || mc.player == null) return;
		float tickDelta = context.tickCounter().getGameTimeDeltaPartialTick(true);
		Vec3 cam = context.camera().position();

		for (AbstractClientPlayer player : mc.level.players()) {
			boolean self = player == mc.player;
			if (self && !context.camera().isDetached()) continue;
			if (!self && !others.get()) continue;
			if (player.isInvisible()) continue;
			drawHat(player, tickDelta, cam);
		}

	}

	private void drawHat(AbstractClientPlayer player, float tickDelta, Vec3 cam) {
		Vec3 pos = player.getPosition(tickDelta);
		float yaw = Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot);
		float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
		Matrix4f m = new Matrix4f()
			.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
			.mul(EmoteAnchor.head(player.getUUID(), bodyYaw, yaw, player.getBbHeight(), 0.03f));

		float r = radius.getFloat();
		float h = height.getFloat();
		int accent = Theme.accent();
		int light = Theme.accentLight();

		int tip = ColorUtil.withAlpha(light, 0.75f);
		WorldRender.geometry(ZvPipelines.WORLD_COLOR_TYPE, buffer -> {
			for (int i = 0; i < SEGMENTS; i++) {
				double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
				// two-tone gradient around the rim
				int c0 = ColorUtil.withAlpha(ColorUtil.lerp(accent, light, (float) (0.5 + 0.5 * Math.sin(a0))), 0.35f);
				int c1 = ColorUtil.withAlpha(ColorUtil.lerp(accent, light, (float) (0.5 + 0.5 * Math.sin(a1))), 0.35f);
				buffer.addVertex(m, 0, h, 0).setColor(tip);
				buffer.addVertex(m, (float) (Math.cos(a0) * r), 0, (float) (Math.sin(a0) * r)).setColor(c0);
				buffer.addVertex(m, (float) (Math.cos(a1) * r), 0, (float) (Math.sin(a1) * r)).setColor(c1);
				buffer.addVertex(m, (float) (Math.cos(a1) * r), 0, (float) (Math.sin(a1) * r)).setColor(c1);
			}
		});
		// the rim: a thin bright ring in the plane of the brim
		Matrix4f brim = new Matrix4f(m).rotateX((float) Math.toRadians(90));
		WorldRender.ring(brim, r, 0.02f, ColorUtil.withAlpha(accent, 0.9f), ColorUtil.withAlpha(light, 0.9f), 0);
	}
}
