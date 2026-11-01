package com.zerovisual.module.impl.visuals;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldContext;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/** Numbers rising above entities when their health changes. */
public class DamageNumbers extends Module {
	private static final long LIFETIME = 1100;

	public final BooleanSetting healing = add(new BooleanSetting("Healing", true));
	public final BooleanSetting self = add(new BooleanSetting("Self", false));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1, 0.5, 2, 0.1));

	private final Map<LivingEntity, Float> lastHealth = new WeakHashMap<>();
	private final List<Number> numbers = new ArrayList<>();
	private final Random random = new Random();

	public DamageNumbers() {
		super("DamageNumbers", "Floating damage and healing numbers", Category.VISUALS);
	}

	@Override
	protected void onDisable() {
		lastHealth.clear();
		numbers.clear();
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) return;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || (living == mc.player && !self.get())) continue;
			if (living.distanceToSqr(mc.player) > 32 * 32) continue;
			float health = living.getHealth() + living.getAbsorptionAmount();
			Float previous = lastHealth.put(living, health);
			if (previous == null) continue;
			float diff = health - previous;
			if (diff < -0.01f || (healing.get() && diff > 0.49f)) {
				Vec3 pos = living.position().add((random.nextDouble() - 0.5) * living.getBbWidth(), living.getBbHeight() + 0.2, (random.nextDouble() - 0.5) * living.getBbWidth());
				numbers.add(new Number(pos, diff, System.currentTimeMillis()));
			}
		}
	}

	public void render(WorldContext context) {
		if (numbers.isEmpty()) return;
		Vec3 cam = context.camera().position();
		long now = System.currentTimeMillis();
		Font font = mc.font;

		Iterator<Number> it = numbers.iterator();
		while (it.hasNext()) {
			Number n = it.next();
			float t = (now - n.start) / (float) LIFETIME;
			if (t >= 1) {
				it.remove();
				continue;
			}
			float rise = (1f - (1f - t) * (1f - t)) * 0.7f;
			// pop in, then shrink a little
			float pop = t < 0.15f ? 0.6f + t / 0.15f * 0.6f : 1.2f - (t - 0.15f) * 0.3f;
			float s = 0.025f * scale.getFloat() * pop;
			float alpha = t > 0.6f ? 1f - (t - 0.6f) / 0.4f : 1f;

			boolean damage = n.amount < 0;
			String label = String.format(Locale.ROOT, damage ? "%.1f" : "+%.1f", Math.abs(n.amount));
			int base = damage ? ColorUtil.lerp(Theme.accentLight(), 0xFFFF4D5E, Math.min(1, Math.abs(n.amount) / 8f)) : 0xFF5CFF8A;
			int color = ColorUtil.withAlpha(base, Math.max(0.05f, alpha));

			Component text = Render2D.styled(label);
			Matrix4f m = new Matrix4f()
				.translate((float) (n.pos.x - cam.x), (float) (n.pos.y - cam.y) + rise, (float) (n.pos.z - cam.z))
				.rotate(context.camera().rotation())
				.scale(s, -s, s);
			WorldRender.text(m, text, -font.width(text) / 2f, 0, color, 0, false, true);
		}
	}

	private record Number(Vec3 pos, float amount, long start) {
	}
}
