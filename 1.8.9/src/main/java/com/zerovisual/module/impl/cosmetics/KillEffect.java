package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.util.TargetTracker;
import com.zerovisual.render.WorldRenderContext;
import com.zerovisual.util.Vec3d;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Effect when the entity you were fighting dies. Client-side only: nobody else sees it,
 * the lightning is cosmetic and does no damage.
 * Black hole: the body stays standing and looks down, a black hole slowly comes down from the sky,
 * pulls the body in, shrinks and collapses with a shockwave.
 */
public class KillEffect extends Module implements com.zerovisual.ZeroVisualClient.WorldEffect {
	private static final long DURATION = 1200;
	private static final float SOUL_MILLIS = 1900;
	private static final int SOUL_SPARKS = 16;
	private static int nextClientId = -1_000_000;

	// black hole timeline, seconds
	private static final float DESCEND_END = 1.4f;
	private static final float ABSORB_END = 2.3f;
	private static final float COLLAPSE_END = 2.85f;
	private static final float HOLE_END = 3.45f;

	public final ModeSetting style = add(new ModeSetting("Style", "Black hole", "Black hole", "Shockwave", "Lightning", "Soul"));
	public final BooleanSetting sound = add(new BooleanSetting("Sound", true));

	private final List<Wave> waves = new ArrayList<>();
	private final List<Wave> souls = new ArrayList<>();
	private final List<Hole> holes = new ArrayList<>();
	/** Dying entities hidden while their copy is pulled into a black hole (EntityRenderDispatcherMixin). */
	private final Set<Integer> hidden = Collections.synchronizedSet(new HashSet<>());
	private EntityLivingBase watched;

	public KillEffect() {
		super("KillEffect", "Effect when you kill a target (only you see it)", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		waves.clear();
		souls.clear();
		holes.clear();
		hidden.clear();
		watched = null;
	}

	public boolean isHidden(Entity entity) {
		return !hidden.isEmpty() && hidden.contains(entity.getEntityId());
	}

	@Override
	public void onTick() {
		if (mc.theWorld == null || mc.thePlayer == null) return;
		EntityLivingBase recent = TargetTracker.lastAttacked(5000);
		if (recent != null && recent != watched && recent.isEntityAlive()) watched = recent;
		if (watched != null && (watched.isDead || watched.getHealth() <= 0)) {
			trigger(watched);
			watched = null;
		}
	}

	/** Starts the effect for a dying entity (also used by the dev screenshots). */
	public void trigger(EntityLivingBase entity) {
		Vec3d pos = Vec3d.pos(entity);
		if (style.is("Lightning")) {
			// a weather effect of the client world: only drawn and heard here, it hurts nothing
			EntityLightningBolt bolt = new EntityLightningBolt(mc.theWorld, pos.x, pos.y, pos.z);
			bolt.setEntityId(nextClientId--);
			mc.theWorld.addWeatherEffect(bolt);
		} else if (style.is("Black hole")) {
			startHole(entity);
		} else if (style.is("Soul")) {
			souls.add(new Wave(pos, System.currentTimeMillis()));
		} else {
			waves.add(new Wave(pos, System.currentTimeMillis()));
			if (sound.get()) mc.thePlayer.playSound("random.orb", 0.6f, 0.6f);
		}
	}

	private void startHole(EntityLivingBase entity) {
		holes.add(new Hole(entity, Vec3d.pos(entity), entity.height, System.currentTimeMillis()));
		hidden.add(entity.getEntityId());
		if (sound.get()) mc.thePlayer.playSound("mob.endermen.portal", 0.5f, 0.5f);
	}

	@Override
	public void render(WorldRenderContext context) {
		if (!souls.isEmpty()) renderSouls(context);
		if (!holes.isEmpty()) renderHoles(context);
		if (waves.isEmpty()) return;
		Vec3d cam = context.camera().getPos();
		long now = System.currentTimeMillis();
		int accent = Theme.accent();
		int light = Theme.accentLight();

		WorldRender.begin();
		Iterator<Wave> it = waves.iterator();
		while (it.hasNext()) {
			Wave wave = it.next();
			float t = (now - wave.start) / (float) DURATION;
			if (t >= 1) {
				it.remove();
				continue;
			}
			// three rings expanding one after another, plus a vertical flash
			for (int i = 0; i < 3; i++) {
				float local = Math.max(0, Math.min(1, t * 1.4f - i * 0.18f));
				if (local <= 0 || local >= 1) continue;
				float eased = 1f - (1f - local) * (1f - local);
				float alpha = 1f - local;
				Vec3d pos = wave.pos.add(0, 0.05 + i * 0.35, 0);
				WorldRender.ring(WorldRender.flat(pos, cam), 0.2f + eased * 2.2f, 0.06f,
					ColorUtil.withAlpha(accent, alpha), ColorUtil.withAlpha(light, alpha), 0.15f);
			}
			float flash = 1f - Math.min(1, t * 2.5f);
			if (flash > 0) {
				Vec3d mid = wave.pos.add(0, 1, 0);
				WorldRender.glowDot(WorldRender.billboard(mid, cam, context.camera().getRotation()), 0.25f + (1 - flash) * 0.4f,
					ColorUtil.withAlpha(light, flash));
			}
		}
		WorldRender.end();
	}

	// ---- Black hole ----

	private void renderHoles(WorldRenderContext context) {
		Vec3d cam = context.camera().getPos();
		Quaternionf rotation = context.camera().getRotation();
		long now = System.currentTimeMillis();

		Iterator<Hole> it = holes.iterator();
		while (it.hasNext()) {
			Hole hole = it.next();
			float s = (now - hole.start) / 1000f;
			if (s >= HOLE_END) {
				hidden.remove(hole.entityId);
				it.remove();
				continue;
			}
			if (s >= ABSORB_END && !hole.collapsed) {
				hole.collapsed = true;
				if (sound.get()) mc.thePlayer.playSound("random.fizz", 0.6f, 0.7f);
			}

			// where the hole ends up: just above the head
			Vec3d rest = hole.pos.add(0, hole.height + 0.9, 0);
			float descend = easeOut(Math.min(1f, s / DESCEND_END));
			Vec3d center = rest.add(0, 7 * (1 - descend), 0);

			float holeSize = 0.55f + 0.15f * Math.max(0, Math.min(1, (s - DESCEND_END) / (ABSORB_END - DESCEND_END)));
			if (s > ABSORB_END) holeSize *= 1f - easeIn(Math.min(1f, (s - ABSORB_END) / (COLLAPSE_END - ABSORB_END)));
			float appear = Math.min(1f, s / 0.35f);

			// the body: stays until the hole arrives, then is pulled up into it, shrinking and spinning
			if (s < ABSORB_END) {
				float pull = s < DESCEND_END ? 0 : easeIn((s - DESCEND_END) / (ABSORB_END - DESCEND_END));
				renderBody(hole, cam, center, pull, context.tickCounter().getTickDelta(true));
			}

			WorldRender.begin();
			if (s < COLLAPSE_END && holeSize > 0.01f) drawHole(center, cam, rotation, holeSize, s, appear);
			if (s >= COLLAPSE_END) {
				float w = (s - COLLAPSE_END) / (HOLE_END - COLLAPSE_END);
				drawShockwave(center, cam, rotation, w);
			}
			WorldRender.end();
		}
	}

	/** The dying entity, standing up again, pulled into the hole while it shrinks and spins. */
	private void renderBody(Hole hole, Vec3d cam, Vec3d center, float pull, float partialTicks) {
		float scale = 1f - pull;
		if (scale <= 0.02f) return;
		EntityLivingBase e = hole.entity;
		// the feet travel up to the hole while the body shrinks around its middle
		Vec3d feet = hole.pos.lerp(center.subtract(0, hole.height * 0.5 * scale, 0), pull);
		int deathTime = e.deathTime, hurtTime = e.hurtTime;
		float limbs = e.limbSwingAmount, prevLimbs = e.prevLimbSwingAmount, pitch = e.rotationPitch, prevPitch = e.prevRotationPitch;
		e.deathTime = 0;
		e.hurtTime = 0;
		e.limbSwingAmount = e.prevLimbSwingAmount = 0;
		e.rotationPitch = e.prevRotationPitch = 45f;
		GlStateManager.pushMatrix();
		GlStateManager.translate(feet.x - cam.x, feet.y - cam.y + hole.height * 0.5 * scale, feet.z - cam.z);
		GlStateManager.rotate(pull * pull * 720f, 0, 1, 0);
		GlStateManager.scale(scale, scale, scale);
		GlStateManager.translate(0, -hole.height * 0.5, 0);
		GlStateManager.enableTexture2D();
		GlStateManager.enableAlpha();
		GlStateManager.depthMask(true);
		GlStateManager.enableCull();
		RenderHelper.enableStandardItemLighting();
		mc.getRenderManager().renderEntityWithPosYaw(e, 0, 0, 0, e.rotationYaw, partialTicks);
		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableLighting();
		GlStateManager.popMatrix();
		e.deathTime = deathTime;
		e.hurtTime = hurtTime;
		e.limbSwingAmount = limbs;
		e.prevLimbSwingAmount = prevLimbs;
		e.rotationPitch = pitch;
		e.prevRotationPitch = prevPitch;
		WorldRender.begin();
	}

	/** Black core with a glowing accretion ring and particles circling around it, facing the camera. */
	private static void drawHole(Vec3d center, Vec3d cam, Quaternionf rotation, float r, float seconds, float appear) {
		int blue = 0xFF388CFF, purple = 0xFFB050F5;
		Matrix4f m = WorldRender.billboard(center, cam, rotation);
		float spin = seconds * (seconds > ABSORB_END ? 900f : 220f);
		Matrix4f spun = new Matrix4f(m).rotateZ((float) Math.toRadians(spin));

		int glowA = ColorUtil.withAlpha(purple, 0.45f * appear), glowB = ColorUtil.withAlpha(blue, 0.45f * appear);
		Render2D.shape(spun, -r * 2.2f, -r * 2.2f, r * 4.4f, r * 4.4f, r * 2.2f, 0, r * 1.6f, glowA, glowB, glowA, glowB);
		int ringA = ColorUtil.withAlpha(blue, appear), ringB = ColorUtil.withAlpha(purple, appear);
		Render2D.shape(spun, -r * 1.18f, -r * 1.18f, r * 2.36f, r * 2.36f, r * 1.18f, r * 0.16f, r * 0.12f, ringA, ringB, ringA, ringB);
		int core = ColorUtil.withAlpha(0xFF000000, appear);
		Render2D.shape(m, -r, -r, r * 2, r * 2, r, 0, r * 0.06f, core, core, core, core);

		// matter spiralling in
		for (int i = 0; i < 8; i++) {
			double a = Math.toRadians(spin * 1.6f + i * 45);
			float orbit = r * (1.5f + 0.5f * (float) Math.sin(seconds * 3 + i));
			Matrix4f dot = new Matrix4f(m).translate((float) Math.cos(a) * orbit, (float) Math.sin(a) * orbit, 0.01f);
			WorldRender.glowDot(dot, r * 0.05f, ColorUtil.withAlpha(i % 2 == 0 ? blue : purple, 0.9f * appear));
		}
	}

	private static void drawShockwave(Vec3d center, Vec3d cam, Quaternionf rotation, float w) {
		int blue = 0xFF388CFF, purple = 0xFFB050F5;
		float alpha = 1f - w;
		float r = 0.15f + 2.4f * easeOut(w);
		int a = ColorUtil.withAlpha(blue, alpha), b = ColorUtil.withAlpha(purple, alpha);
		WorldRender.ring(WorldRender.billboard(center, cam, rotation), r, 0.07f * alpha + 0.01f, a, b, 0.2f);
		WorldRender.ring(WorldRender.flat(center, cam), r * 0.8f, 0.05f * alpha + 0.01f, b, a, 0.15f);
		float flash = 1f - Math.min(1f, w * 3f);
		if (flash > 0) WorldRender.glowDot(WorldRender.billboard(center, cam, rotation), 0.12f + 0.2f * w, ColorUtil.withAlpha(0xFFFFFFFF, flash));
	}

	private static float easeOut(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	private static float easeIn(float t) {
		return t * t * t;
	}

	/** The soul leaves: a beam of light from the body, sparks spiralling up along it and a ring at the feet. */
	private void renderSouls(WorldRenderContext context) {
		Vec3d cam = context.camera().getPos();
		long now = System.currentTimeMillis();
		int accent = Theme.accent(), light = Theme.accentLight();
		Iterator<Wave> it = souls.iterator();
		while (it.hasNext()) {
			Wave soul = it.next();
			float t = (now - soul.start) / SOUL_MILLIS;
			if (t >= 1) {
				it.remove();
				continue;
			}
			float fade = t < 0.65f ? 1f : 1f - (t - 0.65f) / 0.35f;
			float x = (float) (soul.pos.x - cam.x), y = (float) (soul.pos.y - cam.y), z = (float) (soul.pos.z - cam.z);
			// the beam always turns its wide side to the camera
			float yaw = (float) Math.atan2(x, z);
			float width = 0.8f * (1f - t * 0.6f), height = 3f + t * 7f;
			Matrix4f beam = new Matrix4f().translate(x, y, z).rotateY(yaw);
			int core = ColorUtil.withAlpha(light, 0.6f * fade), edge = ColorUtil.withAlpha(accent, 0f);
			WorldRender.quadsAdditive(q -> {
				q.vertex(beam, -width, 0, 0, edge);
				q.vertex(beam, 0, 0, 0, core);
				q.vertex(beam, 0, height, 0, edge);
				q.vertex(beam, -width * 0.3f, height, 0, edge);
				q.vertex(beam, 0, 0, 0, core);
				q.vertex(beam, width, 0, 0, edge);
				q.vertex(beam, width * 0.3f, height, 0, edge);
				q.vertex(beam, 0, height, 0, edge);
			});
			WorldRender.ring(WorldRender.flat(soul.pos.add(0, 0.05, 0), cam), 0.35f + t * 1.4f, 0.05f,
				ColorUtil.withAlpha(accent, fade), ColorUtil.withAlpha(light, fade), 0.14f);
			for (int i = 0; i < SOUL_SPARKS; i++) {
				float local = (t * 1.5f - i * 0.03f);
				if (local <= 0 || local >= 1) continue;
				double angle = i * 2.4 + local * 9;
				float r = 0.62f * (1f - local * 0.7f);
				Vec3d at = soul.pos.add(Math.cos(angle) * r, 0.2 + local * (2.4 + i * 0.12), Math.sin(angle) * r);
				WorldRender.glowDot(WorldRender.billboard(at, cam, context.camera().getRotation()), 0.055f * (1f - local * 0.6f),
					ColorUtil.withAlpha(i % 2 == 0 ? light : accent, (1f - local) * fade));
			}
		}
	}

	private static final class Wave {
		final Vec3d pos;
		final long start;

		Wave(Vec3d pos, long start) {
			this.pos = pos;
			this.start = start;
		}

		public Vec3d pos() {
			return pos;
		}

		public long start() {
			return start;
		}

	}

	private static final class Hole {
		final EntityLivingBase entity;
		final int entityId;
		final Vec3d pos;
		final float height;
		final long start;
		boolean collapsed;

		Hole(EntityLivingBase entity, Vec3d pos, float height, long start) {
			this.entity = entity;
			this.entityId = entity.getEntityId();
			this.pos = pos;
			this.height = height;
			this.start = start;
		}
	}

}
