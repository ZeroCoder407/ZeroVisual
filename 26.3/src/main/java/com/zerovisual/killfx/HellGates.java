package com.zerovisual.killfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.module.impl.cosmetics.KillEffect;
import com.zerovisual.pets.BoxKit;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.WorldContext;
import com.zerovisual.render.ZvPipelines;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * KillEffect "Hell": a medieval wooden door, one and a half player heights tall, rises behind the
 * victim. Its twin doors swing open, fire pours out of the dark, charred hands reach through the
 * flames, grab the body and drag it inside; the doors slam shut, the seam flashes and the door sinks
 * back into the earth. Client-side only, no gore.
 */
public final class HellGates {
	private static final float OPEN_START = 0.5f, OPEN_END = 1.0f;
	private static final float DRAG_START = 0.95f, DRAG_END = 2.1f;
	private static final float SHUT_START = 2.15f, SHUT_END = 2.5f;
	private static final float SINK_START = 2.95f, END = 3.6f;
	private static final int HANDS = 8;

	private static final int WOOD = 0xFF4A3423;
	private static final int WOOD_DARK = 0xFF2E2015;
	private static final int WOOD_BURNT = 0xFF17100B;
	private static final int IRON = 0xFF2B2B33;
	private static final int FIRE = 0xFFFF8A2E;
	private static final int FIRE_DEEP = 0xFFD8340C;
	private static final int HAND = 0xFF6B3A26;

	private final KillEffect module;
	private final List<Gate> gates = new ArrayList<>();

	public HellGates(KillEffect module) {
		this.module = module;
	}

	public void start(LivingEntity entity) {
		gates.add(new Gate(CapturedBody.of(entity), entity.getYRot(), System.currentTimeMillis()));
		module.hide(entity.getId());
		if (module.soundEnabled() && mc().player != null) mc().player.playSound(SoundEvents.PORTAL_TRIGGER, 0.55f, 0.6f);
	}

	public void clear() {
		gates.clear();
	}

	private static net.minecraft.client.Minecraft mc() {
		return net.minecraft.client.Minecraft.getInstance();
	}

	public void render(WorldContext context) {
		Vec3 cam = context.camera().position();
		boolean first = true;
		Iterator<Gate> it = gates.iterator();
		while (it.hasNext()) {
			Gate g = it.next();
			float s = (now(g.start)) / 1000f;
			if (s >= END) {
				module.unhide(g.body.entityId);
				it.remove();
				continue;
			}
			if (!g.opened && s >= OPEN_START + 0.2f) {
				g.opened = true;
				if (module.soundEnabled() && mc().player != null) mc().player.playSound(SoundEvents.WOODEN_DOOR_OPEN, 0.9f, 0.55f);
			}
			if (!g.slammed && s >= SHUT_START + 0.1f) {
				g.slammed = true;
				if (module.soundEnabled() && mc().player != null) mc().player.playSound(SoundEvents.WOODEN_DOOR_CLOSE, 1.0f, 0.45f);
			}

			// door rise and sink
			float rise = easeOut(Mth.clamp(s / 0.5f, 0, 1));
			float sink = s > SINK_START ? easeIn((s - SINK_START) / (END - SINK_START)) : 0;
			float y = (float) (g.base.y - 2.9f * (1 - rise) - sink * 1.2f);
			float shake = s < 0.5f ? Mth.sin(s * 60f) * 0.015f * (1 - rise) : 0;
			Vec3 base = new Vec3(g.base.x, y, g.base.z);

			float open = s < OPEN_START ? 0
				: s < OPEN_END ? easeOut((s - OPEN_START) / (OPEN_END - OPEN_START))
				: s < SHUT_START ? 1
				: s < SHUT_END ? 1 - easeIn((s - SHUT_START) / (SHUT_END - SHUT_START))
				: 0;

			Matrix4f door = new Matrix4f()
				.translate((float) (base.x - cam.x) + shake, (float) (base.y - cam.y), (float) (base.z - cam.z))
				.rotateY((float) Math.toRadians(-g.yaw));

			drawGroundGlow(base, cam, s);
			drawRiseDust(door, s);
			drawAftermath(base, cam, s);
			drawFire(door, cam, base, open, s);
			drawDoor(door, open, s);
			drawShutSeam(door, s);

			// the body dragged backwards through the open doors
			float drag = easeInOut(Mth.clamp((s - DRAG_START) / (DRAG_END - DRAG_START), 0, 1));
			Vec3 inside = new Vec3(g.base.x, g.base.y + g.body.height * 0.32f, g.base.z);
			Vec3 bodyPos = g.body.pos.add(inside.subtract(g.body.pos).multiply(drag, drag, drag));
			if (s < DRAG_END) {
				drawBody(g, cam, bodyPos, drag, context);
				// a dark smear on the ground where it was dragged
				WorldRender.quads(q -> {
					Vec3 from = g.body.pos, to = bodyPos;
					for (int i = 0; i < 3; i++) {
						float a0 = i / 3f, a1 = (i + 1) / 3f;
						Vec3 p0 = from.lerp(to, a0), p1 = from.lerp(to, a1);
						int c = ColorUtil.withAlpha(0xFF14100E, 0.4f * drag);
						Matrix4f m = WorldRender.flat(new Vec3((p0.x + p1.x) / 2, g.base.y + 0.03, (p0.z + p1.z) / 2), cam);
						float r = 0.28f;
						q.vertex(m, -r, 0, -r * 0.5f, c);
						q.vertex(m, r, 0, -r * 0.5f, c);
						q.vertex(m, r, 0, r * 0.5f, c);
						q.vertex(m, -r, 0, r * 0.5f, c);
					}
				});
			}

			drawHands(g, base, cam, bodyPos, s);
			drawSlamSmoke(door, s);
		}
	}

	/** When the doors shut: a hot seam of light and smoke curling out of it. */
	private void drawSlamSmoke(Matrix4f door, float s) {
		float t = (s - SHUT_START) / 0.9f;
		if (t < 0 || t >= 1) return;
		float alpha = (1 - t);
		WorldRender.quadsAdditive(q -> {
			int c = ColorUtil.withAlpha(FIRE, 0.9f * alpha * (1 - t * 0.6f));
			q.vertex(door, -0.01f, 0.14f, 0.07f, c);
			q.vertex(door, 0.01f, 0.14f, 0.07f, c);
			q.vertex(door, 0.01f, 2.42f, 0.07f, ColorUtil.withAlpha(FIRE, 0.2f * alpha));
			q.vertex(door, -0.01f, 2.42f, 0.07f, ColorUtil.withAlpha(FIRE, 0.2f * alpha));
		});
		WorldRender.quadsAdditive(q -> {
			for (int i = 0; i < 8; i++) {
				float life = t * 1.4f - i * 0.05f;
				if (life < 0 || life > 1) continue;
				float side = ((i % 2 == 0 ? 1 : -1)) * (0.05f + life * 0.4f);
				int c = ColorUtil.withAlpha(FIRE, (1 - life) * 0.9f);
				q.vertex(door, side - 0.012f, 1.2f + (i % 3) * 0.4f + life * 0.3f, 0.08f, c);
				q.vertex(door, side + 0.012f, 1.2f + (i % 3) * 0.4f + life * 0.3f, 0.08f, c);
				q.vertex(door, side + 0.012f, 1.24f + (i % 3) * 0.4f + life * 0.3f, 0.08f, c);
				q.vertex(door, side - 0.012f, 1.24f + (i % 3) * 0.4f + life * 0.3f, 0.08f, c);
			}
		});
		WorldRender.quads(q -> {
			for (int i = 0; i < 5; i++) {
				float up = t * (0.6f + i * 0.18f);
				float y = 0.4f + (i / 5f) * 1.8f + up;
				float side = (i % 2 == 0 ? 1 : -1) * (0.1f + t * 0.35f);
				int c = ColorUtil.withAlpha(0xFF8A8A96, 0.35f * alpha);
				q.vertex(door, side - 0.12f, y, 0.1f, c);
				q.vertex(door, side + 0.12f, y, 0.1f, c);
				q.vertex(door, side + 0.16f, y + 0.2f, 0.1f, ColorUtil.withAlpha(0xFF8A8A96, 0f));
				q.vertex(door, side - 0.16f, y + 0.2f, 0.1f, ColorUtil.withAlpha(0xFF8A8A96, 0f));
			}
		});
	}

	private static long now(long start) {
		return System.currentTimeMillis() - start;
	}

	/** Frame plus two plank doors that swing on their hinges; charring and irons for the medieval look. */
	private void drawDoor(Matrix4f door, float open, float s) {
		WorldRender.geometry(ZvPipelines.WORLD_COLOR_SOLID_TYPE, b -> {
			// thick posts, lintel, pointed arch and finial
			BoxKit.box(b, door, -0.92f, 0, -0.13f, -0.74f, 2.55f, 0.13f, WOOD_DARK);
			BoxKit.box(b, door, 0.74f, 0, -0.13f, 0.92f, 2.55f, 0.13f, WOOD_DARK);
			BoxKit.box(b, door, -0.98f, 2.55f, -0.15f, 0.98f, 2.7f, 0.15f, WOOD);
			// the gable: two sloped roof planes with a ridge beam and end caps
			BoxKit.quad(b, door, -0.55f, 2.7f, 0.16f, 0f, 3.0f, 0.16f, 0f, 3.0f, -0.16f, -0.55f, 2.7f, -0.16f, -0.7f, 1f, 0f, WOOD_DARK);
			BoxKit.quad(b, door, 0.55f, 2.7f, -0.16f, 0f, 3.0f, -0.16f, 0f, 3.0f, 0.16f, 0.55f, 2.7f, 0.16f, 0.7f, 1f, 0f, WOOD_DARK);
			BoxKit.box(b, door, -0.06f, 2.97f, -0.18f, 0.06f, 3.03f, 0.18f, 0xFF241812);
			BoxKit.tri(b, door, -0.6f, 2.66f, 0.18f, 0.6f, 2.66f, 0.18f, 0f, 3.03f, 0.18f, WOOD);
			BoxKit.tri(b, door, 0.6f, 2.66f, -0.18f, -0.6f, 2.66f, -0.18f, 0f, 3.03f, -0.18f, WOOD);
			BoxKit.box(b, door, -0.06f, 3.02f, -0.06f, 0.06f, 3.14f, 0.06f, IRON);
			// a ram skull sits on the keystone: cranium, hollow eyes, jaw and curling horns
			BoxKit.box(b, door, -0.13f, 3.03f, -0.1f, 0.13f, 3.2f, 0.1f, 0xFFCFC6B4);
			BoxKit.box(b, door, -0.09f, 2.98f, -0.06f, 0.09f, 3.04f, 0.06f, 0xFFB9AE9A);
			BoxKit.box(b, door, -0.095f, 3.1f, 0.1f, -0.035f, 3.15f, 0.11f, 0xFF1A1410);
			BoxKit.box(b, door, 0.035f, 3.1f, 0.1f, 0.095f, 3.15f, 0.11f, 0xFF1A1410);
			BoxKit.box(b, door, -0.02f, 3.04f, 0.1f, 0.02f, 3.08f, 0.11f, 0xFF1A1410);
			for (int side = -1; side <= 1; side += 2) {
				Matrix4f horn = new Matrix4f(door).translate(side * 0.14f, 3.16f, 0).rotateZ(side * -1.9f);
				BoxKit.taper(b, horn, -0.035f, 0, -0.035f, 0.035f, 0.22f, 0.035f, 0.3f, 0xFFBFB4A0);
				horn.translate(0, 0.19f, 0).rotateZ(side * 0.9f);
				BoxKit.taper(b, horn, -0.026f, 0, -0.026f, 0.026f, 0.17f, 0.026f, 0.35f, 0xFFA79C88);
			}
			// stone threshold
			BoxKit.box(b, door, -0.98f, 0, -0.24f, 0.98f, 0.12f, 0.24f, 0xFF3A3A40);
			// stone arch bricks with mortar gaps and a keystone
			for (int row = 0; row < 2; row++) {
				for (int bx = -2; bx <= 2; bx++) {
					float x0 = bx * 0.4f - 0.18f, x1 = x0 + 0.34f;
					float y0 = 2.72f + row * 0.24f;
					BoxKit.box(b, door, x0, y0 - 0.04f, -0.17f, x1, y0, 0.17f, 0xFF4A4A52);
				}
			}
			BoxKit.box(b, door, -0.09f, 2.7f, -0.19f, 0.09f, 3.02f, 0.19f, 0xFF585862);
			// chains hanging beside the posts
			for (int side = -1; side <= 1; side += 2) {
				for (int link = 0; link < 6; link++) {
					float y = 1.9f - link * 0.17f;
					BoxKit.box(b, door, side * 0.86f - 0.035f, y, -0.05f, side * 0.86f + 0.035f, y + 0.12f, 0.05f,
						link % 2 == 0 ? 0xFF34343C : 0xFF26262E);
				}
			}
			// torch sconces and lantern cages on top of the posts
			for (int side = -1; side <= 1; side += 2) {
				BoxKit.box(b, door, side * 1.06f - 0.04f, 1.55f, -0.04f, side * 1.06f + 0.04f, 1.75f, 0.04f, 0xFF2E2015);
				BoxKit.box(b, door, side * 1.06f - 0.06f, 1.75f, -0.06f, side * 1.06f + 0.06f, 1.82f, 0.06f, 0xFF17100B);
				BoxKit.box(b, door, side * 0.86f - 0.09f, 2.55f, -0.09f, side * 0.86f + 0.09f, 2.62f, 0.09f, IRON);
				BoxKit.box(b, door, side * 0.86f - 0.07f, 2.62f, -0.07f, side * 0.86f + 0.07f, 2.74f, 0.07f, 0xFF3A2A18);
				BoxKit.box(b, door, side * 0.86f - 0.08f, 2.74f, -0.08f, side * 0.86f + 0.08f, 2.78f, 0.08f, IRON);
			}
		});
		// lantern glows and torch flames
		WorldRender.quadsAdditive(q -> {
			for (int side = -1; side <= 1; side += 2) {
				int lc = ColorUtil.withAlpha(FIRE, 0.5f + 0.25f * Mth.sin(s * 6f + side * 2f));
				q.vertex(door, side * 0.86f - 0.06f, 2.63f, 0.075f, lc);
				q.vertex(door, side * 0.86f + 0.06f, 2.63f, 0.075f, lc);
				q.vertex(door, side * 0.86f + 0.06f, 2.73f, 0.075f, lc);
				q.vertex(door, side * 0.86f - 0.06f, 2.73f, 0.075f, lc);
				float flick = 0.6f + 0.4f * Mth.sin(s * 13f + side);
				float x = side * 1.06f;
				int c = ColorUtil.withAlpha(FIRE, 0.75f);
				q.vertex(door, x - 0.05f, 1.82f, 0, c);
				q.vertex(door, x + 0.05f, 1.82f, 0, c);
				q.vertex(door, x, 1.82f + 0.22f * flick + 0.08f, 0, ColorUtil.withAlpha(FIRE, 0f));
				q.vertex(door, x, 1.82f + 0.22f * flick + 0.08f, 0, ColorUtil.withAlpha(FIRE, 0f));
			}
		});
		// the two panels: hinge at the posts, planks, iron bands and ring handles
		for (int side = -1; side <= 1; side += 2) {
			final int sd = side;
			Matrix4f hinge = new Matrix4f(door).translate(sd * 0.74f, 0.12f, 0);
			hinge.rotateY((float) Math.toRadians(-sd * open * 112f));
			WorldRender.geometry(ZvPipelines.WORLD_COLOR_SOLID_TYPE, b -> {
				float x0 = sd > 0 ? -0.72f : 0, x1 = sd > 0 ? 0 : 0.72f;
				// three planks per door, alternating tone, with grooves between
				for (int p = 0; p < 3; p++) {
					float px0 = x0 + (x1 - x0) * p / 3f, px1 = x0 + (x1 - x0) * (p + 1) / 3f;
					BoxKit.box(b, hinge, px0 + 0.008f, 0, -0.045f, px1 - 0.008f, 2.43f, 0.045f, p % 2 == 0 ? WOOD : 0xFF553B28);
					BoxKit.box(b, hinge, px0 - 0.008f, 0.05f, -0.05f, px0 + 0.008f, 2.38f, 0.05f, WOOD_DARK);
				}
				// a light bevel along the outer edges
				BoxKit.box(b, hinge, x0, 2.4f, -0.052f, x1, 2.43f, 0.052f, 0xFF5F4530);
				BoxKit.box(b, hinge, x0, 0, 0.046f, x1, 0.02f, 0.052f, 0xFF5F4530);
				// iron bands with studs
				for (int band = 0; band < 3; band++) {
					float by = 0.35f + band * 0.85f;
					BoxKit.box(b, hinge, x0, by, -0.055f, x1, by + 0.09f, 0.055f, IRON);
					BoxKit.box(b, hinge, x0 + 0.12f, by + 0.02f, 0.056f, x0 + 0.2f, by + 0.07f, 0.075f, 0xFF43434E);
					BoxKit.box(b, hinge, x1 - 0.2f, by + 0.02f, 0.056f, x1 - 0.12f, by + 0.07f, 0.075f, 0xFF43434E);
				}
				// charred lower edges
				BoxKit.box(b, hinge, x0, 0, -0.047f, x1, 0.28f, 0.047f, WOOD_BURNT);
				// ring handle built from four segments, with a back plate
				float hx0 = sd > 0 ? -0.16f : 0.1f, hx1 = sd > 0 ? -0.12f : 0.14f;
				BoxKit.box(b, hinge, hx0 - 0.01f, 1.13f, 0.055f, hx1 + 0.01f, 1.17f, 0.075f, IRON);
				BoxKit.box(b, hinge, hx0, 1.02f, 0.07f, hx0 + 0.015f, 1.14f, 0.085f, IRON);
				BoxKit.box(b, hinge, hx1 - 0.015f, 1.02f, 0.07f, hx1, 1.14f, 0.085f, IRON);
				BoxKit.box(b, hinge, hx0, 0.985f, 0.07f, hx1, 1.02f, 0.085f, 0xFF3A3A44);
				// hinge plates on the outer edge and nails along the planks
				for (int hp = 0; hp < 2; hp++) {
					float hy = 0.5f + hp * 1.15f;
					BoxKit.box(b, hinge, sd > 0 ? -0.12f : 0, hy, 0.05f, sd > 0 ? 0 : 0.12f, hy + 0.16f, 0.07f, IRON);
				}
				for (int n = 0; n < 6; n++) {
					float nx = x0 + 0.1f + (n % 3) * 0.26f, ny = 0.5f + (n / 3) * 1.5f;
					BoxKit.box(b, hinge, nx - 0.015f, ny, 0.05f, nx + 0.015f, ny + 0.03f, 0.065f, 0xFF3E3E48);
				}
				// recessed panel with a raised trim, and diagonal braces like on old cellars
				float px0 = x0 + (sd > 0 ? 0.09f : 0.11f), px1 = x1 - (sd > 0 ? 0.11f : 0.09f);
				BoxKit.box(b, hinge, px0, 0.35f, -0.058f, px1, 1.35f, 0.058f, WOOD_DARK);
				BoxKit.box(b, hinge, px0 + 0.02f, 0.37f, -0.052f, px1 - 0.02f, 1.33f, 0.052f, 0xFF3C2A1C);
				BoxKit.box(b, hinge, px0, 1.45f, -0.058f, px1, 2.25f, 0.058f, WOOD_DARK);
				BoxKit.box(b, hinge, px0 + 0.02f, 1.47f, -0.052f, px1 - 0.02f, 2.23f, 0.052f, 0xFF3C2A1C);
				BoxKit.box(b, hinge, x0 + 0.05f, 0.3f, 0.055f, x0 + 0.14f, 0.9f, 0.07f, WOOD_DARK);
				// half of a carved grim face: brow, sunken eye, cheek groove
				float fx0 = sd > 0 ? -0.62f : 0.3f, fx1 = sd > 0 ? -0.28f : 0.62f;
				BoxKit.box(b, hinge, fx0, 1.42f, 0.05f, fx1, 1.48f, 0.065f, WOOD_DARK);
				BoxKit.box(b, hinge, sd > 0 ? fx0 + 0.04f : fx0, 1.3f, 0.05f, sd > 0 ? fx1 - 0.04f : fx1, 1.42f, 0.06f, WOOD_BURNT);
				BoxKit.box(b, hinge, sd > 0 ? -0.5f : 0.38f, 1.2f, 0.05f, sd > 0 ? -0.3f : 0.58f, 1.28f, 0.062f, 0xFF3A281A);
				BoxKit.box(b, hinge, sd > 0 ? -0.44f : 0.32f, 1.1f, 0.05f, sd > 0 ? -0.4f : 0.5f, 1.22f, 0.06f, WOOD_DARK);
				// a vertical crack filled with ember light
				BoxKit.box(b, hinge, x0 + 0.3f, 0.25f, 0.048f, x0 + 0.32f, 1.3f, 0.056f, WOOD_BURNT);
			});
			// ember glow on the charred part
			WorldRender.quadsAdditive(q -> {
				float x0 = sd > 0 ? -0.72f : 0, x1 = sd > 0 ? 0 : 0.72f;
				int c = ColorUtil.withAlpha(FIRE_DEEP, 0.5f + 0.3f * Mth.sin(s * 9f + sd));
				q.vertex(hinge, x0, 0.05f, 0.048f, c);
				q.vertex(hinge, x1, 0.05f, 0.048f, c);
				q.vertex(hinge, x1, 0.2f, 0.048f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
				q.vertex(hinge, x0, 0.2f, 0.048f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
			});
		}
	}

	/** While the doors are shut, light and smoke seep out of the seam between them. */
	private void drawShutSeam(Matrix4f door, float s) {
		if (s < 0.12f) return;
		float heat = Mth.clamp((s - 0.12f) / 0.35f, 0, 1) * (s < OPEN_START ? 1 : 0);
		float settle = s > SHUT_START ? Mth.clamp((s - SHUT_START) / 0.3f, 0, 1) * (1 - Mth.clamp((s - SHUT_START - 0.4f) / 0.6f, 0, 1)) : 0;
		float glow = Math.max(heat, settle);
		if (glow <= 0.02f) return;
		WorldRender.quadsAdditive(q -> {
			int c = ColorUtil.withAlpha(FIRE, 0.85f * glow * (0.7f + 0.3f * Mth.sin(s * 14f)));
			q.vertex(door, -0.012f, 0.12f, 0.062f, ColorUtil.withAlpha(FIRE, 0f));
			q.vertex(door, 0.012f, 0.12f, 0.062f, ColorUtil.withAlpha(FIRE, 0f));
			q.vertex(door, 0.012f, 2.45f, 0.062f, c);
			q.vertex(door, -0.012f, 2.45f, 0.062f, c);
		});
		// smoke curling from the top of the seam
		if (heat > 0.1f) {
			WorldRender.quads(q -> {
				for (int i = 0; i < 4; i++) {
					float life = ((s * 0.8f + i * 0.24f) % 0.9f) / 0.9f;
					float y = 2.35f + life * 1.1f;
					float sway = Mth.sin(s * 2f + i * 1.8f) * 0.12f;
					int c = ColorUtil.withAlpha(0xFF6E6A74, (1 - life) * 0.35f * heat);
					q.vertex(door, sway - 0.16f - life * 0.2f, y, -0.1f, c);
					q.vertex(door, sway + 0.16f + life * 0.2f, y, -0.1f, c);
					q.vertex(door, sway + 0.2f + life * 0.3f, y + 0.35f, -0.1f, ColorUtil.withAlpha(0xFF6E6A74, 0f));
					q.vertex(door, sway - 0.2f - life * 0.3f, y + 0.35f, -0.1f, ColorUtil.withAlpha(0xFF6E6A74, 0f));
				}
			});
		}
	}

	/** The darkness inside the open doorway and fire pouring out of it. */
	private void drawFire(Matrix4f door, Vec3 cam, Vec3 base, float open, float s) {
		if (open <= 0.03f) return;
		// black inside (normal blending: additive would add nothing for black)
		WorldRender.quads(q -> {
			int c = ColorUtil.withAlpha(0xFF050303, 0.92f * open);
			q.vertex(door, -0.72f * open, 0.12f, -0.01f, c);
			q.vertex(door, 0.72f * open, 0.12f, -0.01f, c);
			q.vertex(door, 0.72f * open, 2.5f, -0.01f, c);
			q.vertex(door, -0.72f * open, 2.5f, -0.01f, c);
		});
		// layered flames: a deep red body, an orange heart and a yellow core, each tongue swaying
		WorldRender.quadsAdditive(q -> {
			int[] layers = {FIRE_DEEP, FIRE, 0xFFFFD66A};
			float[] layerScale = {1f, 0.68f, 0.38f};
			float[] layerAlpha = {0.4f, 0.42f, 0.5f};
			for (int layer = 0; layer < 3; layer++) {
				for (int i = 0; i < 6; i++) {
					float seed = i * 0.53f + layer * 1.7f;
					float flick = Mth.sin(s * (8f + i * 1.3f) + seed * 5f) * 0.5f + 0.5f;
					float sway = Mth.sin(s * 3.2f + seed) * 0.09f * open;
					float x = ((i % 3) - 1) * 0.3f * open;
					float width = (0.11f + 0.05f * flick) * open * layerScale[layer];
					float height = (0.85f + 0.55f * flick) * open * layerScale[layer];
					float y0 = 0.1f + (i % 2) * 0.14f + layer * 0.03f;
					int c0 = ColorUtil.withAlpha(layers[layer], layerAlpha[layer] * open);
					int c1 = ColorUtil.withAlpha(layers[layer], 0f);
					// lobed tongue: wide base, waist, rounded tip
					q.vertex(door, x - width + sway, y0, 0.055f + layer * 0.004f, c0);
					q.vertex(door, x + width + sway, y0, 0.055f + layer * 0.004f, c0);
					q.vertex(door, x + width * 0.55f + sway, y0 + height * 0.55f, 0.055f + layer * 0.004f, ColorUtil.withAlpha(layers[layer], layerAlpha[layer] * 0.6f * open));
					q.vertex(door, x - width * 0.55f + sway, y0 + height * 0.55f, 0.055f + layer * 0.004f, ColorUtil.withAlpha(layers[layer], layerAlpha[layer] * 0.6f * open));
					q.vertex(door, x - width * 0.55f + sway, y0 + height * 0.55f, 0.055f + layer * 0.004f, ColorUtil.withAlpha(layers[layer], layerAlpha[layer] * 0.6f * open));
					q.vertex(door, x + width * 0.55f + sway, y0 + height * 0.55f, 0.055f + layer * 0.004f, ColorUtil.withAlpha(layers[layer], layerAlpha[layer] * 0.6f * open));
					q.vertex(door, x + width * 0.22f + sway * 1.4f, y0 + height, 0.055f + layer * 0.004f, c1);
					q.vertex(door, x - width * 0.22f + sway * 1.4f, y0 + height, 0.055f + layer * 0.004f, c1);
				}
			}
			// a bright glow across the whole doorway
			int glow = ColorUtil.withAlpha(FIRE_DEEP, 0.16f * open * (0.8f + 0.2f * Mth.sin(s * 10f)));
			q.vertex(door, -0.72f * open, 0.12f, -0.005f, glow);
			q.vertex(door, 0.72f * open, 0.12f, -0.005f, glow);
			q.vertex(door, 0.72f * open, 2.5f, -0.005f, glow);
			q.vertex(door, -0.72f * open, 2.5f, -0.005f, glow);
		});
		// a soft furnace glow filling the whole opening
		WorldRender.quadsAdditive(q -> {
			int c = ColorUtil.withAlpha(FIRE_DEEP, 0.3f * open * (0.85f + 0.15f * Mth.sin(s * 6f)));
			float w = 0.9f * open + 0.1f;
			q.vertex(door, -w, 0.15f, -0.02f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
			q.vertex(door, w, 0.15f, -0.02f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
			q.vertex(door, w, 2.6f, -0.02f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
			q.vertex(door, -w, 2.6f, -0.02f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
			q.vertex(door, -0.75f * open, 0.15f, -0.015f, c);
			q.vertex(door, 0.75f * open, 0.15f, -0.015f, c);
			q.vertex(door, 0.75f * open, 2.5f, -0.015f, c);
			q.vertex(door, -0.75f * open, 2.5f, -0.015f, c);
		});
		// fire licking out over the threshold, in front of the doors
		WorldRender.quadsAdditive(q -> {
			for (int i = 0; i < 5; i++) {
				float flick = Mth.sin(s * 9f + i * 1.9f) * 0.5f + 0.5f;
				float x = (i - 2) * 0.26f * open;
				float reach = (0.22f + 0.2f * flick) * open;
				int c = ColorUtil.withAlpha(FIRE, 0.55f * open);
				q.vertex(door, x - 0.07f, 0.12f, 0.16f, c);
				q.vertex(door, x + 0.07f, 0.12f, 0.16f, c);
				q.vertex(door, x + 0.04f, 0.12f + reach, 0.16f + reach * 0.5f, ColorUtil.withAlpha(FIRE, 0f));
				q.vertex(door, x - 0.04f, 0.12f + reach, 0.16f + reach * 0.5f, ColorUtil.withAlpha(FIRE, 0f));
			}
		});
		// the fire paints warm light on the inner faces of the opened doors
		WorldRender.quadsAdditive(q -> {
			for (int side = -1; side <= 1; side += 2) {
				int c = ColorUtil.withAlpha(FIRE_DEEP, 0.18f * open * (0.8f + 0.2f * Mth.sin(s * 8f + side)));
				q.vertex(door, side * 0.74f, 0.15f, 0.05f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
				q.vertex(door, side * 0.74f, 0.15f, -0.05f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
				q.vertex(door, side * 0.74f, 2.2f, -0.05f, c);
				q.vertex(door, side * 0.74f, 2.2f, 0.05f, c);
			}
		});
		// glowing rim all around the opening
		WorldRender.quadsAdditive(q -> {
			int c = ColorUtil.withAlpha(FIRE, 0.35f * open * (0.8f + 0.2f * Mth.sin(s * 7f)));
			float w = 0.74f * open, hgt = 2.5f;
			q.vertex(door, -w - 0.03f, 0.12f, 0.07f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
			q.vertex(door, -w, 0.12f, 0.07f, c);
			q.vertex(door, -w, hgt, 0.07f, c);
			q.vertex(door, -w - 0.03f, hgt, 0.07f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
			q.vertex(door, w, 0.12f, 0.07f, c);
			q.vertex(door, w + 0.03f, 0.12f, 0.07f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
			q.vertex(door, w + 0.03f, hgt, 0.07f, ColorUtil.withAlpha(FIRE_DEEP, 0f));
			q.vertex(door, w, hgt, 0.07f, c);
		});
		// ember sparks rising out of the fire
		WorldRender.quadsAdditive(q -> {
			for (int i = 0; i < 10; i++) {
				float seed = i * 0.7f;
				float life = (s * 0.7f + seed) % 1f;
				float x = ((i * 47) % 100 / 100f - 0.5f) * 1.1f * open;
				float y = 0.3f + life * 2.0f;
				int c = ColorUtil.withAlpha(FIRE, (1 - life) * 0.8f * open);
				q.vertex(door, x - 0.012f, y, 0.05f, c);
				q.vertex(door, x + 0.012f, y, 0.05f, c);
				q.vertex(door, x + 0.012f, y + 0.035f, 0.05f, c);
				q.vertex(door, x - 0.012f, y + 0.035f, 0.05f, c);
			}
		});
		// firelight circle on the ground
		WorldRender.quadsAdditive(q -> {
			Matrix4f m = WorldRender.flat(base.add(0, 0.04, 0), cam);
			int c = ColorUtil.withAlpha(FIRE, 0.3f * open);
			q.vertex(m, -1.1f, 0, -0.8f, c);
			q.vertex(m, 1.1f, 0, -0.8f, c);
			q.vertex(m, 1.1f, 0, 0.8f, c);
			q.vertex(m, -1.1f, 0, 0.8f, c);
		});
	}

	/** Dust and small stones kicked up while the gate tears out of the ground. */
	private void drawRiseDust(Matrix4f door, float s) {
		float t = s / 0.75f;
		if (t >= 1) return;
		WorldRender.quads(q -> {
			for (int i = 0; i < 7; i++) {
				float side = (i - 3) * 0.28f;
				float up = t * (0.3f + (i % 3) * 0.15f);
				int c = ColorUtil.withAlpha(0xFF8A8068, (1 - t) * 0.45f);
				q.vertex(door, side - 0.09f, up, 0.2f, c);
				q.vertex(door, side + 0.09f, up, 0.2f, c);
				q.vertex(door, side + 0.13f, up + 0.18f, 0.24f, ColorUtil.withAlpha(0xFF8A8068, 0f));
				q.vertex(door, side - 0.13f, up + 0.18f, 0.24f, ColorUtil.withAlpha(0xFF8A8068, 0f));
			}
		});
	}

	/** Burnt ground and a few embers still glowing after the gate has sunk. */
	private void drawAftermath(Vec3 base, Vec3 cam, float s) {
		float t = (s - SINK_START) / (END - SINK_START);
		if (t < 0 || t >= 1) return;
		WorldRender.quads(q -> {
			Matrix4f m = WorldRender.flat(base.add(0, 0.035, 0), cam);
			int c = ColorUtil.withAlpha(0xFF1E1512, 0.75f * (1 - t));
			q.vertex(m, -0.95f, 0, -0.4f, c);
			q.vertex(m, 0.95f, 0, -0.4f, c);
			q.vertex(m, 0.95f, 0, 0.4f, c);
			q.vertex(m, -0.95f, 0, 0.4f, c);
		});
		WorldRender.quadsAdditive(q -> {
			for (int i = 0; i < 8; i++) {
				float life = (t * 1.6f + i * 0.125f) % 1f;
				float x = ((i * 37) % 100 / 100f - 0.5f) * 1.6f;
				float z = ((i * 53) % 100 / 100f - 0.5f) * 0.7f;
				int c = ColorUtil.withAlpha(FIRE_DEEP, (1 - life) * (1 - t) * 0.8f);
				q.vertex(new Matrix4f(), (float) (base.x - cam.x) + x - 0.012f, (float) (base.y - cam.y) + 0.05f + life * 0.8f, (float) (base.z - cam.z) + z, c);
				q.vertex(new Matrix4f(), (float) (base.x - cam.x) + x + 0.012f, (float) (base.y - cam.y) + 0.05f + life * 0.8f, (float) (base.z - cam.z) + z, c);
				q.vertex(new Matrix4f(), (float) (base.x - cam.x) + x + 0.012f, (float) (base.y - cam.y) + 0.09f + life * 0.8f, (float) (base.z - cam.z) + z, ColorUtil.withAlpha(FIRE_DEEP, 0f));
				q.vertex(new Matrix4f(), (float) (base.x - cam.x) + x - 0.012f, (float) (base.y - cam.y) + 0.09f + life * 0.8f, (float) (base.z - cam.z) + z, ColorUtil.withAlpha(FIRE_DEEP, 0f));
			}
		});
	}

	private void drawGroundGlow(Vec3 base, Vec3 cam, float s) {
		float alpha = 0.35f * (1 - Mth.clamp((s - SINK_START) / 0.5f, 0, 1));
		if (alpha <= 0.01f) return;
		WorldRender.quadsAdditive(q -> {
			Matrix4f m = WorldRender.flat(base.add(0, 0.03, 0), cam);
			int c = ColorUtil.withAlpha(0xFF8A2A0C, alpha);
			q.vertex(m, -1.3f, 0, -0.5f, c);
			q.vertex(m, 1.3f, 0, -0.5f, c);
			q.vertex(m, 1.3f, 0, 0.5f, c);
			q.vertex(m, -1.3f, 0, 0.5f, c);
		});
	}

	private void drawBody(Gate g, Vec3 cam, Vec3 bodyPos, float drag, WorldContext context) {
		float scale = drag > 0.8f ? 1f - (drag - 0.8f) / 0.2f * 0.9f : 1f;
		PoseStack matrices = new PoseStack();
		matrices.translate(bodyPos.x - cam.x, bodyPos.y - cam.y, bodyPos.z - cam.z);
		// dragged backwards: the body tips over towards the door
		matrices.rotate(new Quaternionf().rotateX((float) Math.toRadians(-drag * 24f))
			.rotateY((float) Math.toRadians(drag * 140f)));
		matrices.scale(scale, scale, scale);
		g.body.submit(matrices, context.raw().submitNodeCollector(), context.cameraState());
	}

	/** Hands with palms and curling fingers reach out of the fire and grab. */
	private void drawHands(Gate g, Vec3 base, Vec3 cam, Vec3 bodyPos, float s) {
		for (int i = 0; i < HANDS; i++) {
			float startAt = 0.85f + i * 0.12f;
			float reach = easeOut(Mth.clamp((s - startAt) / 0.65f, 0, 1));
			float retract = easeIn(Mth.clamp((s - 2.05f) / 0.4f, 0, 1));
			float eff = reach * (1 - retract);
			if (eff <= 0.001f) continue;

			float hx = ((i * 41) % 100 / 100f - 0.5f) * 1.0f;
			float hy = 0.45f + ((i * 29) % 100 / 100f) * 1.55f;
			Vec3 start = rotateYaw(new Vec3(hx, hy, 0.02f), g.yaw).add(base);

			double ang = i * 2.5 + s * 0.6;
			Vec3 grab = bodyPos.add(Math.cos(ang) * 0.26, 0.2f + (i % 3) * 0.32, Math.sin(ang) * 0.26);
			Vec3 end = start.add(grab.subtract(start).multiply(eff, eff, eff));
			Vec3 mid = start.add(end.subtract(start).multiply(0.55f, 0.55f, 0.55f)).add(0, -0.06f * Mth.sin(eff * Mth.PI), 0);

			WorldRender.geometry(ZvPipelines.WORLD_COLOR_SOLID_TYPE, b -> {
				segment(b, start, mid, 0.05f, 0.038f, cam);
				segment(b, mid, end, 0.038f, 0.03f, cam);
				float curl = Mth.clamp((reach - 0.55f) / 0.45f, 0, 1) * (1 - retract);
				Vec3 toBody = grab.subtract(end).normalize();
				// palm
				Matrix4f palm = aim(end, end.add(toBody), cam);
				BoxKit.box(b, palm, -0.045f, -0.05f, 0, 0.045f, 0.05f, 0.075f, HAND);
				// four fingers in two segments and a thumb, all curling shut
				for (int f = -2; f <= 2; f++) {
					if (f == 0) continue;
					Matrix4f proximal = new Matrix4f(palm).translate(f * 0.018f, (f > 0 ? 0.033f : -0.033f), 0.07f)
						.rotateX(-curl * 1.15f).rotateZ(f * 0.1f);
					BoxKit.box(b, proximal, -0.007f, -0.007f, 0, 0.007f, 0.007f, 0.045f, HAND);
					Matrix4f distal = new Matrix4f(proximal).translate(0, 0, 0.042f).rotateX(-curl * 1.2f);
					BoxKit.box(b, distal, -0.006f, -0.006f, 0, 0.006f, 0.006f, 0.038f, 0xFF7A4630);
				}
			});
			WorldRender.glowDot(aim(mid, end, cam), 0.06f, ColorUtil.withAlpha(FIRE, 0.8f * eff));
		}
	}

	private static void segment(com.mojang.blaze3d.vertex.VertexConsumer b, Vec3 a, Vec3 to, float w0, float w1, Vec3 cam) {
		double dx = to.x - a.x, dy = to.y - a.y, dz = to.z - a.z;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 0.01) return;
		BoxKit.taper(b, aim(a, to, cam), -w1, 0, -w1, w1, (float) len, w1, w1 / w0, HAND);
	}

	private static Matrix4f aim(Vec3 from, Vec3 to, Vec3 cam) {
		double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
		float yaw = (float) Math.atan2(dx, dz);
		float pitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
		return new Matrix4f()
			.translate((float) (from.x - cam.x), (float) (from.y - cam.y), (float) (from.z - cam.z))
			.rotateY(yaw)
			.rotateX(-pitch);
	}

	private static Vec3 rotateYaw(Vec3 v, float yawDeg) {
		double rad = Math.toRadians(yawDeg);
		double cos = Math.cos(rad), sin = Math.sin(rad);
		return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
	}

	private static float easeOut(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	private static float easeIn(float t) {
		return t * t * t;
	}

	private static float easeInOut(float t) {
		return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
	}

	private static final class Gate {
		final CapturedBody body;
		final float yaw;
		final Vec3 base;
		final long start;
		boolean opened;
		boolean slammed;

		Gate(CapturedBody body, float yaw, long start) {
			this.body = body;
			this.yaw = yaw;
			this.start = start;
			// the door stands right behind the victim's back
			this.base = body.pos.add(rotateYaw(new Vec3(0, 0, -1.1), yaw));
		}
	}
}
