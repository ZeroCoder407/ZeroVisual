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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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
public class KillEffect extends Module {
	private static final long DURATION = 1200;
	private static int nextClientId = -1_000_000;

	// black hole timeline, seconds
	private static final float DESCEND_END = 1.4f;
	private static final float ABSORB_END = 2.3f;
	private static final float COLLAPSE_END = 2.85f;
	private static final float HOLE_END = 3.45f;

	public final ModeSetting style = add(new ModeSetting("Style", "Black hole", "Black hole", "Shockwave", "Lightning"));
	public final BooleanSetting sound = add(new BooleanSetting("Sound", true));

	private final List<Wave> waves = new ArrayList<>();
	private final List<Hole> holes = new ArrayList<>();
	/** Dying entities hidden while their copy is pulled into a black hole (EntityRenderDispatcherMixin). */
	private final Set<Integer> hidden = Collections.synchronizedSet(new HashSet<>());
	private LivingEntity watched;

	public KillEffect() {
		super("KillEffect", "Effect when you kill a target (only you see it)", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		waves.clear();
		holes.clear();
		hidden.clear();
		watched = null;
	}

	public boolean isHidden(Entity entity) {
		return !hidden.isEmpty() && hidden.contains(entity.getId());
	}

	@Override
	public void onTick() {
		if (mc.world == null || mc.player == null) return;
		LivingEntity recent = TargetTracker.lastAttacked(5000);
		if (recent != null && recent != watched && recent.isAlive()) watched = recent;
		if (watched != null && (watched.isDead() || watched.getHealth() <= 0)) {
			trigger(watched);
			watched = null;
		}
	}

	/** Starts the effect for a dying entity (also used by the dev screenshots). */
	public void trigger(LivingEntity entity) {
		Vec3d pos = entity.getPos();
		if (style.is("Lightning")) {
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(mc.world, SpawnReason.TRIGGERED);
			if (bolt != null) {
				// a negative id can never collide with a real (server) entity, addEntity removes duplicates by id
				bolt.setId(nextClientId--);
				bolt.refreshPositionAfterTeleport(pos);
				bolt.setCosmetic(true);
				mc.world.addEntity(bolt);
			}
		} else if (style.is("Black hole")) {
			startHole(entity);
		} else {
			waves.add(new Wave(pos, System.currentTimeMillis()));
			if (sound.get()) mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.6f);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void startHole(LivingEntity entity) {
		EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
		// an own copy of the render state: the renderer reuses its cached one for every entity
		EntityRenderState state = renderer.createRenderState();
		renderer.updateRenderState(entity, state, 1f);
		state.displayName = null;
		state.onFire = false;
		if (state instanceof LivingEntityRenderState living) {
			// standing up again and looking down at the ground
			living.deathTime = 0;
			living.hurt = false;
			living.pitch = 45f;
			living.limbAmplitudeMultiplier = 0;
		}
		int light = mc.getEntityRenderDispatcher().getLight(entity, 1f);
		holes.add(new Hole(state, renderer, entity.getId(), entity.getPos(), entity.getHeight(), light, System.currentTimeMillis()));
		hidden.add(entity.getId());
		if (sound.get()) mc.player.playSound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.5f);
	}

	public void render(WorldRenderContext context) {
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
		VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

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
				if (sound.get()) mc.player.playSound(SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), 0.6f, 0.7f);
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
				renderBody(hole, cam, center, pull, consumers);
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

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void renderBody(Hole hole, Vec3d cam, Vec3d center, float pull, VertexConsumerProvider.Immediate consumers) {
		float scale = 1f - pull;
		if (scale <= 0.02f) return;
		// the feet travel up to the hole while the body shrinks around its middle
		Vec3d feet = hole.pos.lerp(center.subtract(0, hole.height * 0.5 * scale, 0), pull);
		MatrixStack matrices = new MatrixStack();
		matrices.translate(feet.x - cam.x, feet.y - cam.y, feet.z - cam.z);
		matrices.translate(0, hole.height * 0.5 * scale, 0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pull * pull * 720f));
		matrices.scale(scale, scale, scale);
		matrices.translate(0, -hole.height * 0.5, 0);
		Vec3d offset = hole.renderer.getPositionOffset(hole.state);
		matrices.translate(offset.x, offset.y, offset.z);
		hole.renderer.render(hole.state, matrices, consumers, hole.light);
		consumers.draw();
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

	private record Wave(Vec3d pos, long start) {
	}

	private static final class Hole {
		final EntityRenderState state;
		@SuppressWarnings("rawtypes")
		final EntityRenderer renderer;
		final int entityId;
		final Vec3d pos;
		final float height;
		final int light;
		final long start;
		boolean collapsed;

		@SuppressWarnings("rawtypes")
		Hole(EntityRenderState state, EntityRenderer renderer, int entityId, Vec3d pos, float height, int light, long start) {
			this.state = state;
			this.renderer = renderer;
			this.entityId = entityId;
			this.pos = pos;
			this.height = height;
			this.light = light;
			this.start = start;
		}
	}
}
