package com.zerovisual.module.impl.cosmetics;

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
			Vec3d pos = player.getLerpedPos(tickDelta).add(0, player.getHeight() + 0.2 + bob, 0);
			Matrix4f m = WorldRender.flat(pos, cam);
			WorldRender.ring(m, radius.getFloat(), 0.045f, Theme.accentLight(), Theme.accent(), 0.08f);
		}
		WorldRender.end();
	}
}
