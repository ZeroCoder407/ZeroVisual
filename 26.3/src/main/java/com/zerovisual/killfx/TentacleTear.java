package com.zerovisual.killfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.gui.Theme;
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
 * KillEffect "ArmsLegs": magic lifts the victim into the air and pulls the arms and legs outwards;
 * four tentacles seize the limbs, rip them off in one pull and fling the parts to the ground, where
 * they lie for a moment and fade away. Pure magic, glowing silhouettes instead of a real model being
 * torn apart: no blood, no wounds.
 */
public final class TentacleTear {
	private static final float LIFT_END = 0.55f;
	private static final float SPREAD_END = 1.25f;
	private static final float TEAR = 1.35f;
	private static final float LAND_END = 2.0f;
	private static final float REST_END = 2.9f;
	private static final float END = 3.5f;
	private static final int ARMS = 4;

	private static final int BASE_PURPLE = 0xFF5B2FB8;
	private static final int TIP_CYAN = 0xFF4FD8E8;
	private static final int SHARD = 0xFF2A2438;

	private final KillEffect module;
	private final List<Tear> tears = new ArrayList<>();

	public TentacleTear(KillEffect module) {
		this.module = module;
	}

	public void start(LivingEntity entity) {
		tears.add(new Tear(CapturedBody.of(entity), System.currentTimeMillis()));
		module.hide(entity.getId());
		if (module.soundEnabled() && mc().player != null) mc().player.playSound(SoundEvents.ENDERMAN_STARE, 0.45f, 0.5f);
	}

	public void clear() {
		tears.clear();
	}

	private static net.minecraft.client.Minecraft mc() {
		return net.minecraft.client.Minecraft.getInstance();
	}

	public void render(WorldContext context) {
		Vec3 cam = context.camera().position();
		long now = System.currentTimeMillis();
		Iterator<Tear> it = tears.iterator();
		while (it.hasNext()) {
			Tear t = it.next();
			float s = (now - t.start) / 1000f;
			if (s >= END) {
				module.unhide(t.body.entityId);
				it.remove();
				continue;
			}
			float lift = easeOut(Mth.clamp(s / LIFT_END, 0, 1));
			float spread = easeInOut(Mth.clamp((s - LIFT_END) / (SPREAD_END - LIFT_END), 0, 1));
			float after = s - TEAR;

			if (s < TEAR) {
				// the body floats up, limbs visibly pulled outwards (stretched a little), shaking harder
				float shake = spread * 0.035f;
				Vec3 jitter = new Vec3(Mth.sin(s * 44f) * shake, Mth.sin(s * 57f) * shake * 0.6f, Mth.cos(s * 39f) * shake);
				drawBody(t, cam, lift, spread, jitter, context);
			} else if (!t.flashed) {
				t.flashed = true;
				if (module.soundEnabled() && mc().player != null) mc().player.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.45f);
			}

			// arm positions around the lifted body (in its local space, then rotated by yaw)
			Vec3 center = t.body.pos.add(0, STRETCH_Y * t.body.height * 0.5f + LIFT_H * lift, 0);
			drawMagicCircle(t, cam, s, lift, spread);

			if (s < TEAR + 0.35f) drawArms(t, cam, s, spread, center, s < TEAR);
			drawFlash(t, cam, s, center);

			if (after > 0) {
				// the parts fly apart, fall, lie on the ground and fade
				drawPieces(t, cam, after, center);
			}
		}
	}

	// body pose constants
	private static final float LIFT_H = 1.35f;
	private static final float STRETCH_Y = 1f;

	private void drawBody(Tear t, Vec3 cam, float lift, float spread, Vec3 jitter, WorldContext context) {
		PoseStack matrices = new PoseStack();
		Vec3 at = t.body.pos.add(jitter).add(0, LIFT_H * lift, 0);
		matrices.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		// limbs pulled out: scale sideways a touch and rotate slowly
		matrices.scale(1f + spread * 0.24f, 1f - spread * 0.06f, 1f);
		matrices.rotate(new Quaternionf().rotateY((float) Math.toRadians(spread * 18f)));
		t.body.submit(matrices, context.raw().submitNodeCollector(), context.cameraState());
	}

	/** Where a limb anchor sits: wrists out to the sides, ankles below and slightly out. */
	private Vec3 limbAnchor(Tear t, int i, Vec3 center) {
		float h = t.body.height;
		double[] angles = {0.6, Math.PI - 0.6, Math.PI * 0.5 - 0.35, Math.PI * 0.5 + 0.35};
		// arms: high and wide; legs: low and slightly out
		boolean arm = i < 2;
		double a = angles[i] + Math.toRadians(t.yaw);
		double radius = arm ? h * 0.42 : h * 0.16;
		double y = arm ? h * 0.08 : -h * 0.42;
		return center.add(Math.cos(a) * radius, y, Math.sin(a) * radius);
	}

	/** Four long tentacles rise from the ground, seize the wrists and ankles and pull outward. */
	private void drawArms(Tear t, Vec3 cam, float s, float spread, Vec3 center, boolean pulling) {
		int light = Theme.accentLight();
		for (int i = 0; i < ARMS; i++) {
			Vec3 anchor = limbAnchor(t, i, center);
			Vec3 ground = t.body.pos.add(anchor.x - center.x, 0.02, anchor.z - center.z);
			float grow = easeOut(Mth.clamp((s - LIFT_END * 0.6f) / 0.6f, 0, 1));
			if (grow <= 0.01f) continue;
			// the tentacle arcs from its ground root to the limb, bowing out
			Vec3 mid = ground.lerp(anchor, 0.5f).add((anchor.x - ground.x) * 0.35, -0.15f, (anchor.z - ground.z) * 0.35);
			float whip = pulling ? Mth.sin(s * 3f + i) * 0.03f : 0;
			DrawTube.draw(cam, ground, mid.add(0, whip, 0), anchor, grow, BASE_PURPLE, TIP_CYAN);
			// a taut line of light from the limb outwards: the magic is pulling
			if (spread > 0.05f && pulling) {
				Vec3 out = new Vec3(anchor.x - center.x, 0, anchor.z - center.z).normalize();
				Vec3 far = anchor.add(out.multiply(0.55 * spread, 0.1 * spread, 0.55 * spread));
				WorldRender.quadsAdditive(q -> {
					int c = ColorUtil.withAlpha(light, 0.5f * spread);
					float x0 = (float) (anchor.x - cam.x), y0 = (float) (anchor.y - cam.y), z0 = (float) (anchor.z - cam.z);
					float x1 = (float) (far.x - cam.x), y1 = (float) (far.y - cam.y), z1 = (float) (far.z - cam.z);
					q.vertex(new Matrix4f(), x0 - 0.015f, y0 - 0.015f, z0, c);
					q.vertex(new Matrix4f(), x0 + 0.015f, y0 + 0.015f, z0, c);
					q.vertex(new Matrix4f(), x1 + 0.01f, y1 + 0.01f, z1, ColorUtil.withAlpha(light, 0f));
					q.vertex(new Matrix4f(), x1 - 0.01f, y1 - 0.01f, z1, ColorUtil.withAlpha(light, 0f));
				});
			}
			// a glowing seize ring around the limb
			Matrix4f at = WorldRender.billboard(anchor, cam, rot());
			WorldRender.ring(at, 0.11f, 0.018f, ColorUtil.withAlpha(light, 0.9f * grow), ColorUtil.withAlpha(accent(t), 0.9f * grow), 0.04f);
			WorldRender.glowDot(at, 0.05f, ColorUtil.withAlpha(light, 0.9f * grow));
		}
	}

	private org.joml.Quaternionf lastRot = new org.joml.Quaternionf();

	private static double orbitAngle(int i, float s) {
		return i * 0.785 + s * 1.4;
	}

	private Quaternionf rot() {
		return lastRot;
	}

	private int accent(Tear t) {
		return Theme.accent();
	}

	/** Rings of magic on the ground and wisps rising under the body. */
	private void drawMagicCircle(Tear t, Vec3 cam, float s, float lift, float spread) {
		float fade = 1 - Mth.clamp((s - REST_END) / (END - REST_END), 0, 1);
		if (fade <= 0.01f) return;
		Vec3 ground = t.body.pos.add(0, 0.03f, 0);
		int accent = Theme.accent(), light = Theme.accentLight();
		Matrix4f flat = WorldRender.flat(ground, cam);
		WorldRender.ring(flat, 0.9f + spread * 0.3f, 0.035f,
			ColorUtil.withAlpha(accent, 0.7f * fade), ColorUtil.withAlpha(light, 0.7f * fade), 0.12f);
		WorldRender.ring(new Matrix4f(flat).rotateZ(s * 0.6f), 0.62f + spread * 0.2f, 0.018f,
			ColorUtil.withAlpha(light, 0.5f * fade), ColorUtil.withAlpha(accent, 0.5f * fade), 0.05f);
		// a pentagram drawn in light inside the outer ring
		WorldRender.quadsAdditive(q -> {
			double[] angles = new double[5];
			for (int i = 0; i < 5; i++) angles[i] = -Math.PI / 2 + i * (Math.PI * 2 / 5) + s * 0.35;
			float r = 0.82f + spread * 0.22f;
			for (int i = 0; i < 5; i++) {
				double a0 = angles[i], a1 = angles[(i + 2) % 5];
				float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
				float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
				int c = ColorUtil.withAlpha(light, 0.45f * fade);
				float dx = x1 - x0, dz = z1 - z0;
				float len = (float) Math.sqrt(dx * dx + dz * dz + 1e-6f);
				float nx = -dz / len * 0.012f, nz = dx / len * 0.012f;
				q.vertex(flat, x0 + nx, 0.012f, z0 + nz, c);
				q.vertex(flat, x0 - nx, 0.012f, z0 - nz, c);
				q.vertex(flat, x1 - nx, 0.012f, z1 - nz, c);
				q.vertex(flat, x1 + nx, 0.012f, z1 + nz, c);
			}
		});
		// glyph triangles turning between the rings
		for (int i = 0; i < 5; i++) {
			double a = i * 1.2566 + s * 0.9;
			float gx = (float) Math.cos(a) * 0.76f, gz = (float) Math.sin(a) * 0.76f;
			Matrix4f g = new Matrix4f(flat).translate(gx, 0.005f, gz).rotateY((float) -a);
			WorldRender.shape(new Matrix4f(g), -0.07f, 0, 0.14f, 0.05f, 0.02f, 0, 0.02f,
				ColorUtil.withAlpha(light, 0.55f * fade), ColorUtil.withAlpha(accent, 0.55f * fade),
				ColorUtil.withAlpha(accent, 0.55f * fade), ColorUtil.withAlpha(light, 0.55f * fade),
				true);
		}
		// cracks split the ground with light as the magic builds up
		float crackGlow = Mth.clamp((s - 0.1f) / 0.5f, 0, 1) * fade;
		if (crackGlow > 0.02f) {
			WorldRender.quadsAdditive(q -> {
				for (int i = 0; i < 5; i++) {
					double a = i * 1.2566 + 0.4;
					double len = 1.5 + (i % 3) * 0.4;
					float x0 = (float) Math.cos(a) * 0.3f, z0 = (float) Math.sin(a) * 0.3f;
					float x1 = (float) Math.cos(a) * (float) len, z1 = (float) Math.sin(a) * (float) len;
					int c = ColorUtil.withAlpha(accent, 0.7f * crackGlow);
					q.vertex(flat, x0 - 0.03f, 0.01f, z0 - 0.03f, ColorUtil.withAlpha(accent, 0f));
					q.vertex(flat, x0 + 0.03f, 0.01f, z0 + 0.03f, ColorUtil.withAlpha(accent, 0f));
					q.vertex(flat, x1 + 0.015f, 0.01f, z1 + 0.015f, c);
					q.vertex(flat, x1 - 0.015f, 0.01f, z1 - 0.015f, c);
				}
			});
		}
		// rune ticks rotating around the circle
		WorldRender.quadsAdditive(q -> {
			for (int i = 0; i < 10; i++) {
				double a = i * 0.628 + s * 0.8;
				float r = 0.95f + spread * 0.3f;
				float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
				int c = ColorUtil.withAlpha(accent, 0.6f * fade);
				q.vertex(flat, x - 0.04f, 0, z - 0.01f, c);
				q.vertex(flat, x + 0.04f, 0, z + 0.01f, c);
				q.vertex(flat, x + 0.04f, 0.09f, z + 0.01f, ColorUtil.withAlpha(accent, 0f));
				q.vertex(flat, x - 0.04f, 0.09f, z - 0.01f, ColorUtil.withAlpha(accent, 0f));
			}
		});
		// a spinning hoop of runes around the lifted body
		if (lift > 0.1f) {
			Vec3 waist = t.body.pos.add(0, LIFT_H * lift + t.body.height * 0.45f, 0);
			Matrix4f hoop = new Matrix4f(WorldRender.billboard(waist, cam, rot())).rotateZ(s * 1.6f);
			WorldRender.ring(hoop, 0.55f, 0.02f,
				ColorUtil.withAlpha(accent, 0.5f * lift * fade), ColorUtil.withAlpha(light, 0.5f * lift * fade), 0.06f);
			for (int i = 0; i < 5; i++) {
				double a = i * 1.2566 + s * 1.6;
				Matrix4f tick = new Matrix4f(hoop).translate((float) Math.cos(a) * 0.55f, (float) Math.sin(a) * 0.55f, 0);
				WorldRender.glowDot(tick, 0.03f, ColorUtil.withAlpha(light, 0.6f * lift * fade));
			}
		}
		// a column of light under the lifted body
		if (lift > 0.05f) {
			WorldRender.quadsAdditive(q -> {
				int cb = ColorUtil.withAlpha(light, 0.22f * lift * fade);
				int ct = ColorUtil.withAlpha(light, 0f);
				float w = 0.3f * lift;
				q.vertex(flat, -w, 0.05f, 0, cb);
				q.vertex(flat, w, 0.05f, 0, cb);
				q.vertex(flat, w * 0.7f, LIFT_H * lift, 0, ct);
				q.vertex(flat, -w * 0.7f, LIFT_H * lift, 0, ct);
			});
		}
		// sparks orbiting the body
		for (int i = 0; i < 8; i++) {
			double a = orbitAngle(i, s);
			float r = 0.55f + 0.1f * Mth.sin(s * 3 + i);
			Vec3 at = t.body.pos.add(Math.cos(a) * r, 0.4 + LIFT_H * lift * Math.max(0.2f, Mth.sin(s * 1.7f + i) * 0.5f + 0.5f), Math.sin(a) * r);
			WorldRender.glowDot(WorldRender.billboard(at, cam, rot()), 0.03f,
				ColorUtil.withAlpha(i % 2 == 0 ? accent : light, 0.6f * fade));
		}
		// a scorched ring fading away after the dust settles
		float burn = Mth.clamp((s - REST_END * 0.7f) / 0.8f, 0, 1);
		if (burn > 0 && burn < 1) {
			WorldRender.ring(WorldRender.flat(t.body.pos.add(0, 0.02, 0), cam), 1.35f, 0.05f,
				ColorUtil.withAlpha(0xFF3A2A20, (1 - burn) * 0.55f), ColorUtil.withAlpha(0xFF2A1E18, (1 - burn) * 0.55f), 0.1f);
		}
		// wisps rising along the lifted body
		for (int i = 0; i < 6; i++) {
			float life = ((s * 0.8f + i * 0.17f) % 1f);
			double a = i * 1.05 + s;
			Vec3 at = t.body.pos.add(Math.cos(a) * 0.45f, 0.1 + life * (LIFT_H + 0.6f), Math.sin(a) * 0.45f);
			WorldRender.glowDot(WorldRender.billboard(at, cam, rot()), 0.04f,
				ColorUtil.withAlpha(i % 2 == 0 ? accent : light, (1 - life) * 0.7f * fade));
		}
	}

	private void drawFlash(Tear t, Vec3 cam, float s, Vec3 center) {
		float at = (s - TEAR) / 0.35f;
		if (at < 0 || at >= 1) return;
		// a burst of sparks thrown out in every direction
		for (int i = 0; i < 14; i++) {
			double a = i * 0.4488 + 0.3;
			double up = 0.35 + (i % 4) * 0.18;
			Vec3 p = center.add(Math.cos(a) * (0.15 + at * 1.1), up * at - 0.4 * at * at, Math.sin(a) * (0.15 + at * 1.1));
			WorldRender.glowDot(WorldRender.billboard(p, cam, rot()), 0.045f * (1 - at),
				ColorUtil.withAlpha(i % 2 == 0 ? Theme.accent() : Theme.accentLight(), (1 - at) * 0.9f));
		}
		WorldRender.glowDot(WorldRender.billboard(center, cam, rot()), 0.16f + at * 0.3f,
			ColorUtil.withAlpha(Theme.accentLight(), (1 - at) * 0.9f));
		WorldRender.ring(WorldRender.billboard(center, cam, rot()), 0.2f + at * 1.1f, 0.03f,
			ColorUtil.withAlpha(Theme.accent(), (1 - at) * 0.8f), ColorUtil.withAlpha(Theme.accentLight(), (1 - at) * 0.8f), 0.08f);
		// six rip lines bursting out of the body for the first moments
		if (at < 0.45f) {
			float burst = 1 - at / 0.45f;
			WorldRender.quadsAdditive(q -> {
				for (int i = 0; i < 6; i++) {
					double a = i * 1.047 + 0.25;
					float x0 = (float) Math.cos(a) * 0.1f, y0 = (float) Math.sin(a) * 0.1f;
					float x1 = (float) Math.cos(a) * (0.7f + (1 - burst) * 0.6f), y1 = (float) Math.sin(a) * (0.7f + (1 - burst) * 0.6f);
					float cx = (float) (center.x - cam.x), cy = (float) (center.y - cam.y), cz = (float) (center.z - cam.z);
					int c = ColorUtil.withAlpha(0xFFFFFFFF, burst * 0.8f);
					q.vertex(new Matrix4f(), cx + x0, cy + y0, cz, ColorUtil.withAlpha(0xFFFFFFFF, 0f));
					q.vertex(new Matrix4f(), cx + x0 * 1.08f, cy + y0 * 1.08f, cz, ColorUtil.withAlpha(0xFFFFFFFF, 0f));
					q.vertex(new Matrix4f(), cx + x1 * 1.02f, cy + y1 * 1.02f, cz, c);
					q.vertex(new Matrix4f(), cx + x1 * 0.98f, cy + y1 * 0.98f, cz, c);
				}
			});
		}
		// a flat shockwave sweeping the ground
		WorldRender.ring(WorldRender.flat(t.body.pos.add(0, 0.04, 0), cam), 0.3f + at * 2.6f, 0.05f * (1 - at) + 0.01f,
			ColorUtil.withAlpha(Theme.accentLight(), (1 - at) * 0.7f), ColorUtil.withAlpha(Theme.accent(), (1 - at) * 0.7f), 0.12f);
	}

	/** Six parts: head, torso, two arms and two legs, flung out, dropped, resting and fading. */
	private void drawPieces(Tear t, Vec3 cam, float dt, Vec3 center) {
		int accent = Theme.accent();
		float fade = 1 - Mth.clamp((dt - (REST_END - TEAR)) / 0.5f, 0, 1);
		if (fade <= 0.02f) return;
		for (int i = 0; i < t.pieces.length; i++) {
			Piece p = t.pieces[i];
			Vec3 at = p.position(dt);
			float ground = (float) (t.body.pos.y + 0.06);
			boolean landed = at.y <= ground + 0.03f;
			if (landed) at = new Vec3(at.x, ground, at.z);
			Matrix4f m = new Matrix4f()
				.translate((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z))
				.rotateY(p.spin).rotateX(p.tumble(dt, landed));
			final int kind = i;
			WorldRender.geometry(ZvPipelines.WORLD_COLOR_SOLID_TYPE, b -> {
				switch (kind) {
					case 0 -> { // head with a jaw
						BoxKit.taper(b, m, -0.12f, -0.12f, -0.1f, 0.12f, 0.12f, 0.1f, 0.86f, SHARD);
						BoxKit.box(b, m, -0.1f, 0.07f, -0.09f, 0.1f, 0.15f, 0.09f, 0xFF38314C);
						BoxKit.box(b, m, -0.06f, -0.14f, 0.05f, 0.06f, -0.11f, 0.1f, 0xFF1A1622);
					}
					case 1 -> { // chest and pelvis
						BoxKit.taper(b, m, -0.16f, -0.26f, -0.11f, 0.16f, 0.26f, 0.11f, 0.78f, SHARD);
						BoxKit.box(b, m, -0.13f, -0.08f, -0.1f, 0.13f, 0.0f, 0.1f, 0xFF332C44);
					}
					case 2, 3 -> { // arms with a fist
						BoxKit.taper(b, m, -0.05f, -0.34f, -0.05f, 0.05f, 0.34f, 0.05f, 0.45f, SHARD);
						BoxKit.box(b, m, -0.055f, -0.42f, -0.055f, 0.055f, -0.33f, 0.055f, 0xFF38314C);
					}
					default -> { // legs with a boot
						BoxKit.taper(b, m, -0.06f, -0.36f, -0.06f, 0.06f, 0.36f, 0.06f, 0.4f, SHARD);
						BoxKit.box(b, m, -0.065f, -0.44f, -0.065f, 0.065f, -0.35f, 0.065f, 0xFF38314C);
					}
				}
			});
			// a bright edge seam along the part, so it reads as torn magic rather than a grey box
			WorldRender.quadsAdditive(q -> {
				int c = ColorUtil.withAlpha(accent, 0.55f * fade);
				float len = kind == 0 ? 0.12f : kind == 1 ? 0.26f : kind == 2 || kind == 3 ? 0.34f : 0.36f;
				q.vertex(m, -len * 0.28f, len, 0.0f, c);
				q.vertex(m, len * 0.28f, len, 0.0f, c);
				q.vertex(m, len * 0.28f, -len, 0.0f, ColorUtil.withAlpha(accent, 0f));
				q.vertex(m, -len * 0.28f, -len, 0.0f, ColorUtil.withAlpha(accent, 0f));
			});
			// a fading trail streak behind the flying part
			if (dt < Piece.LAND_TIME * 1.6f && fade > 0.1f) {
				final Vec3 atT = at;
				Vec3 back = at.add(p.velocity.multiply(-0.13f, -0.13f, -0.13f));
				final Vec3 backT = back;
				WorldRender.quadsAdditive(q -> {
					int c = ColorUtil.withAlpha(accent, 0.35f * fade * (1 - dt / (Piece.LAND_TIME * 1.6f)));
					q.vertex(new Matrix4f(), (float) (backT.x - cam.x) - 0.03f, (float) (backT.y - cam.y), (float) (backT.z - cam.z), ColorUtil.withAlpha(accent, 0f));
					q.vertex(new Matrix4f(), (float) (backT.x - cam.x) + 0.03f, (float) (backT.y - cam.y), (float) (backT.z - cam.z), ColorUtil.withAlpha(accent, 0f));
					q.vertex(new Matrix4f(), (float) (atT.x - cam.x) + 0.03f, (float) (atT.y - cam.y), (float) (atT.z - cam.z), c);
					q.vertex(new Matrix4f(), (float) (atT.x - cam.x) - 0.03f, (float) (atT.y - cam.y), (float) (atT.z - cam.z), c);
				});
			}
			WorldRender.glowDot(new Matrix4f(m), 0.05f, ColorUtil.withAlpha(accent, 0.7f * fade));
			// while fading the part smoulders away into rising sparks
			float dissolve = Mth.clamp((dt - (REST_END - TEAR)) / 0.5f, 0, 1);
			if (dissolve > 0) {
				for (int sp = 0; sp < 3; sp++) {
					float ph = (dt * 2.2f + sp * 0.33f + kind * 0.17f) % 1f;
					Vec3 spark = at.add((sp - 1) * 0.06, 0.1 + ph * 0.5f, (sp % 2 == 0 ? 1 : -1) * 0.04);
					WorldRender.glowDot(WorldRender.billboard(spark, cam, rot()), 0.025f * (1 - ph),
						ColorUtil.withAlpha(accent, (1 - ph) * dissolve * 0.8f));
				}
			}
			// dust kicked up at the impact
			if (landed) {
				final float impact = Mth.clamp((dt - Piece.LAND_TIME) / 0.5f, 0, 1);
				final Vec3 atF = at;
				final float groundF = ground;
				if (impact < 1) {
					WorldRender.quads(q -> {
						for (int d = 0; d < 4; d++) {
							double a = d * 1.57 + p.spin;
							float dx = (float) Math.cos(a) * (0.12f + impact * 0.3f);
							float dz = (float) Math.sin(a) * (0.12f + impact * 0.3f);
							int c = ColorUtil.withAlpha(0xFF9A9480, (1 - impact) * 0.4f);
							float x0 = (float) (atF.x - cam.x) + dx * 0.5f, z0 = (float) (atF.z - cam.z) + dz * 0.5f;
							q.vertex(new Matrix4f(), x0 - 0.06f, (float) (groundF - cam.y) + 0.05f + impact * 0.12f, z0, c);
							q.vertex(new Matrix4f(), x0 + 0.06f, (float) (groundF - cam.y) + 0.05f + impact * 0.12f, z0, c);
							q.vertex(new Matrix4f(), x0 + 0.06f, (float) (groundF - cam.y) + 0.16f + impact * 0.12f, z0, ColorUtil.withAlpha(0xFF9A9480, 0f));
							q.vertex(new Matrix4f(), x0 - 0.06f, (float) (groundF - cam.y) + 0.16f + impact * 0.12f, z0, ColorUtil.withAlpha(0xFF9A9480, 0f));
						}
					});
				}
			}
			// a small impact ring where the part hit the ground
			if (landed) {
				float impact = Mth.clamp((dt - Piece.LAND_TIME) / 0.4f, 0, 1);
				WorldRender.ring(WorldRender.flat(new Vec3(at.x, ground + 0.02, at.z), cam), 0.08f + impact * 0.3f, 0.012f,
					ColorUtil.withAlpha(accent, (1 - impact) * 0.5f), ColorUtil.withAlpha(accent, 0f), 0.03f);
			}
		}
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

	/** A smooth tentacle drawn as overlapping tapered segments along a quadratic curve. */
	private static final class DrawTube {
		static void draw(Vec3 cam, Vec3 a, Vec3 b, Vec3 c, float grow, int colA, int colB) {
			int segs = 14;
			WorldRender.geometry(ZvPipelines.WORLD_COLOR_SOLID_TYPE, v -> {
				Vec3 prev = a;
				for (int k = 1; k <= segs; k++) {
					float u = k / (float) segs * grow;
					Vec3 p = quadBezier(a, b, c, u);
					int color = u < 0.5f ? ColorUtil.lerp(colA, 0xFF8A5CF0, u * 2f) : ColorUtil.lerp(0xFF8A5CF0, colB, (u - 0.5f) * 2f);
					drawSeg(v, cam, prev, p, 0.095f * (1 - u * 0.7f), color);
					// joint bulge keeps the curve smooth instead of beaded
					if (k % 3 == 0) {
						float bw = 0.1f * (1 - u * 0.7f);
						Matrix4f bm = new Matrix4f().translate((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
						BoxKit.box(v, bm, -bw, -bw, -bw, bw, bw, bw, color);
					}
					prev = p;
				}
			});
			// glowing nodes along the tentacle
			for (int k = 2; k <= 10; k += 3) {
				float u = k / (float) segs * grow;
				Vec3 p = quadBezier(a, b, c, u);
				float size = 0.045f * (1 - u * 0.5f);
				WorldRender.glowDot(WorldRender.billboard(p, cam, new org.joml.Quaternionf()), size,
					ColorUtil.withAlpha(0xFFC08BFF, 0.75f));
			}
			// three small claws at the tip
			Vec3 tip = quadBezier(a, b, c, grow);
			Vec3 prevP = quadBezier(a, b, c, Math.max(0f, grow - 0.15f));
			for (int k = -1; k <= 1; k++) {
				final int kk = k;
				WorldRender.geometry(ZvPipelines.WORLD_COLOR_SOLID_TYPE, v -> {
					Matrix4f m = new Matrix4f()
						.translate((float) (tip.x - cam.x), (float) (tip.y - cam.y), (float) (tip.z - cam.z))
						.rotateY((float) Math.atan2(tip.x - prevP.x, tip.z - prevP.z))
						.rotateZ(kk * 0.5f);
					BoxKit.tri(v, m, -0.02f, 0, 0, 0.02f, 0, 0, 0, 0.09f, 0.05f, TIP_CYAN);
				});
			}
			WorldRender.glowDot(WorldRender.billboard(tip, cam, new org.joml.Quaternionf()), 0.06f,
				ColorUtil.withAlpha(TIP_CYAN, 0.9f));
		}

		private static void drawSeg(com.mojang.blaze3d.vertex.VertexConsumer v, Vec3 cam, Vec3 a, Vec3 b, float w, int col) {
			double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 0.005) return;
			float yaw = (float) Math.atan2(dx, dz);
			float pitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
			Matrix4f m = new Matrix4f()
				.translate((float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z))
				.rotateY(yaw).rotateX(-pitch);
			BoxKit.box(v, m, -w, -w, -w * 0.7f, w, w, (float) len + w * 0.7f, col);
		}

		private static Vec3 quadBezier(Vec3 a, Vec3 b, Vec3 c, float t) {
			double u = 1 - t;
			return new Vec3(
				u * u * a.x + 2 * u * t * b.x + t * t * c.x,
				u * u * a.y + 2 * u * t * b.y + t * t * c.y,
				u * u * a.z + 2 * u * t * b.z + t * t * c.z);
		}
	}

	/** One torn-off part: launched outwards, falls with gravity, lands flat and lies still. */
	private static final class Piece {
		final Vec3 start;
		final Vec3 velocity;
		final float spin;
		final float spinSpeed;

		Piece(Vec3 start, Vec3 velocity, float spin, float spinSpeed) {
			this.start = start;
			this.velocity = velocity;
			this.spin = spin;
			this.spinSpeed = spinSpeed;
		}

		/** Flight, then one small bounce and a settle. */
		Vec3 position(float dt) {
			float t = Math.min(dt, LAND_TIME);
			Vec3 flying = start.add(velocity.multiply(t, t, t)).add(0, -4.5 * t * t, 0);
			if (dt <= LAND_TIME) return flying;
			float settle = dt - LAND_TIME;
			float hop = (float) (Math.sin(Math.min(settle / 0.25, 1) * Math.PI) * 0.09 * Math.max(0, 1 - settle / 0.5));
			Vec3 landed = flying.add(velocity.multiply(LAND_TIME, 0, LAND_TIME)).add(0, hop, 0);
			return new Vec3(landed.x, Math.max(flying.y, landed.y), landed.z);
		}

		float tumble(float dt, boolean landed) {
			if (landed) return (float) Math.toRadians(78); // lying flat
			return spin + spinSpeed * Math.min(dt, LAND_TIME) * 9f;
		}

		static final float LAND_TIME = 0.55f;
	}

	private static final class Tear {
		final CapturedBody body;
		final float yaw;
		final long start;
		final Piece[] pieces = new Piece[6];
		boolean flashed;

		Tear(CapturedBody body, long start) {
			this.body = body;
			this.start = start;
			this.yaw = 0;
			float h = body.height;
			// head, torso, two arms, two legs scattered from the limb positions outwards
			pieces[0] = new Piece(body.pos.add(0, LIFT_H + h * 0.92f, 0), new Vec3(0.7, 2.2, 0.35), 0.4f, 1.1f);
			pieces[1] = new Piece(body.pos.add(0, LIFT_H + h * 0.5f, 0), new Vec3(-0.45, 1.6, -0.55), 1.3f, 0.8f);
			pieces[2] = new Piece(body.pos.add(0.62, LIFT_H + h * 0.72f, 0), new Vec3(3.1, 1.1, 0.45), 0.2f, 1.4f);
			pieces[3] = new Piece(body.pos.add(-0.62, LIFT_H + h * 0.72f, 0), new Vec3(-3.1, 1.1, -0.45), 2.1f, 1.2f);
			pieces[4] = new Piece(body.pos.add(0.28, LIFT_H + 0.2f, 0), new Vec3(1.3, 0.3, -0.7), 0.8f, 1.0f);
			pieces[5] = new Piece(body.pos.add(-0.28, LIFT_H + 0.2f, 0), new Vec3(-1.3, 0.3, 0.7), 1.7f, 1.3f);
		}
	}
}
