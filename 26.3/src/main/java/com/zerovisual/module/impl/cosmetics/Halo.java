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
			WorldRender.ring(m, radius.getFloat(), 0.045f, Theme.accentLight(), Theme.accent(), 0.08f);
		}
	}
}
