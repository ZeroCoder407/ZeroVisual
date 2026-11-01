package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.emote.EmoteAnchor;
import com.zerovisual.fx.Shapes;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.ZvPipelines;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldContext;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Translucent gradient wings on your back that slowly flap (third person and other players). */
public class Wings extends Module {
	/** Outline of the right wing (x outwards, y up), root at (0, 0). Alternating radii give the feather tips. */
	private static final float[][] SHAPE = {
		{0.10f, 0.22f}, {0.32f, 0.42f}, {0.55f, 0.55f}, {0.78f, 0.58f}, {0.95f, 0.50f}, {1.00f, 0.32f},
		{0.86f, 0.20f}, {0.98f, 0.05f}, {0.80f, -0.04f}, {0.88f, -0.20f}, {0.66f, -0.24f}, {0.70f, -0.42f},
		{0.48f, -0.40f}, {0.46f, -0.58f}, {0.28f, -0.48f}, {0.12f, -0.30f}
	};

	public final ModeSetting style = add(new ModeSetting("Style", "Angel", "Angel", "Dragon"));
	public final NumberSetting size = add(new NumberSetting("Size", 0.7, 0.4, 1.2, 0.05));
	public final NumberSetting flapSpeed = add(new NumberSetting("Flap speed", 1, 0.2, 3, 0.1));
	public final BooleanSetting others = add(new BooleanSetting("Other players", false));

	public Wings() {
		super("Wings", "Wings on your back (third-person view)", Category.COSMETICS);
	}

	public void render(WorldContext context) {
		if (mc.level == null || mc.player == null) return;
		float tickDelta = context.tickCounter().getGameTimeDeltaPartialTick(true);
		Vec3 cam = context.camera().position();
		float time = (System.currentTimeMillis() % 100_000L) / 1000f;

		for (AbstractClientPlayer player : mc.level.players()) {
			boolean self = player == mc.player;
			if (self && !context.camera().isDetached()) continue;
			if (!self && !others.get()) continue;
			if (player.isInvisible() || player.isFallFlying()) continue;

			Vec3 pos = player.getPosition(tickDelta);
			float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
			// faster flapping while moving
			double speed = player.getDeltaMovement().horizontalDistance();
			float beat = style.is("Dragon") ? 0.6f : 1f;
			float flap = (float) Math.sin(time * flapSpeed.getFloat() * beat * (2.2 + speed * 8)) * (style.is("Dragon") ? 26f : 18f) + 22f;
			float back = player.isCrouching() ? 0.35f : 0.18f;
			float height = player.getBbHeight() * (player.isCrouching() ? 0.7f : 0.74f);

			Matrix4f base = new Matrix4f()
				.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
				.mul(EmoteAnchor.chest(player.getUUID(), bodyYaw, height))
				.translate(0, 0, -back);
			drawWing(base, 1, flap);
			drawWing(base, -1, flap);
		}

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

		if (style.is("Dragon")) {
			Matrix4f dragon = new Matrix4f(m).scale(1.3f);
			int membrane = ColorUtil.withAlpha(ColorUtil.lerp(accent, 0xFF000000, 0.45f), 0.82f);
			int shade = ColorUtil.withAlpha(ColorUtil.lerp(accent, 0xFF000000, 0.7f), 0.92f);
			int bone = ColorUtil.withAlpha(ColorUtil.lerp(light, 0xFF000000, 0.25f), 1f);
			WorldRender.quads(q -> Shapes.dragonWing(q, dragon, membrane, shade, bone));
			return;
		}

		int root = ColorUtil.withAlpha(accent, 0.85f);
		int edge = ColorUtil.withAlpha(light, 0.9f);
		WorldRender.geometry(ZvPipelines.WORLD_COLOR_TYPE, buffer -> {
			// the membrane: quads from the root to two neighbouring outline points
			for (int i = 0; i + 1 < SHAPE.length; i++) {
				float[] p0 = SHAPE[i], p1 = SHAPE[i + 1];
				buffer.addVertex(m, 0, 0, 0).setColor(root);
				buffer.addVertex(m, p0[0], p0[1], 0).setColor(membrane(p0, accent, light));
				buffer.addVertex(m, p1[0], p1[1], 0).setColor(membrane(p1, accent, light));
				buffer.addVertex(m, p1[0], p1[1], 0).setColor(membrane(p1, accent, light));
			}
			// the outline, closed through the root
			float px = 0, py = 0;
			for (float[] p : SHAPE) {
				WorldRender.line(buffer, m, px, py, p[0], p[1], 0.012f, edge, edge);
				px = p[0];
				py = p[1];
			}
			WorldRender.line(buffer, m, px, py, 0, 0, 0.012f, edge, edge);
			// a few feather lines from the root
			for (int i = 1; i < SHAPE.length; i += 3) {
				WorldRender.line(buffer, m, 0, 0, SHAPE[i][0] * 0.85f, SHAPE[i][1] * 0.85f, 0.008f,
					ColorUtil.withAlpha(light, 0.35f), ColorUtil.withAlpha(light, 0.05f));
			}
		});
	}

	private static int membrane(float[] p, int accent, int light) {
		float reach = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1]);
		return ColorUtil.withAlpha(ColorUtil.lerp(accent, light, reach), 0.25f + 0.2f * (1 - reach));
	}
}
