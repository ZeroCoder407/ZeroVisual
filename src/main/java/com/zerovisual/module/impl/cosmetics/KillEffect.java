package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.util.TargetTracker;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effect when the entity you were fighting dies. Client-side only: nobody else sees it,
 * the lightning is cosmetic and does no damage.
 */
public class KillEffect extends Module {
	private static final long DURATION = 1200;
	private static int nextClientId = -1_000_000;

	public final ModeSetting style = add(new ModeSetting("Style", "Shockwave", "Shockwave", "Lightning"));
	public final BooleanSetting sound = add(new BooleanSetting("Sound", true));

	private final List<Wave> waves = new ArrayList<>();
	private LivingEntity watched;

	public KillEffect() {
		super("KillEffect", "Effect when you kill a target (only you see it)", Category.COSMETICS);
	}

	@Override
	protected void onDisable() {
		waves.clear();
		watched = null;
	}

	@Override
	public void onTick() {
		if (mc.world == null || mc.player == null) return;
		LivingEntity recent = TargetTracker.lastAttacked(5000);
		if (recent != null && recent != watched && recent.isAlive()) watched = recent;
		if (watched != null && (watched.isDead() || watched.getHealth() <= 0)) {
			trigger(watched.getPos());
			watched = null;
		}
	}

	private void trigger(Vec3d pos) {
		if (style.is("Lightning")) {
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(mc.world, SpawnReason.TRIGGERED);
			if (bolt != null) {
				// a negative id can never collide with a real (server) entity, addEntity removes duplicates by id
				bolt.setId(nextClientId--);
				bolt.refreshPositionAfterTeleport(pos);
				bolt.setCosmetic(true);
				mc.world.addEntity(bolt);
			}
		} else {
			waves.add(new Wave(pos, System.currentTimeMillis()));
			if (sound.get()) mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.6f);
		}
	}

	public void render(WorldRenderContext context) {
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

	private record Wave(Vec3d pos, long start) {
	}
}
