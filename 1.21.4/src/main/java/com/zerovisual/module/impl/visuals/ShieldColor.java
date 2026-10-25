package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Tints shields by whether they can block: green while ready, red while an axe has disabled them.
 * The tint goes through the entity overlay texture (OverlayTextureMixin, rows {@link #READY_ROW} and
 * {@link #BROKEN_ROW}), which the shader mixes over the shield texture just like the red hurt flash.
 * Own shield: the item cooldown. Other players: the shield break event that everyone hears, for as long as
 * an axe disables a shield.
 */
public class ShieldColor extends Module {
	/** Overlay texture rows vanilla never reads (it uses 3 for hurt and 10 for normal). */
	public static final int READY_ROW = 0, BROKEN_ROW = 1;
	/** How long an axe disables a shield. */
	private static final int DISABLE_TICKS = 100;

	public final BooleanSetting ownShield = add(new BooleanSetting("Own shield", true));
	public final BooleanSetting otherShields = add(new BooleanSetting("Other players", true));
	public final ColorSetting readyColor = add(new ColorSetting("Ready color", 0xFF22C55E));
	public final ColorSetting brokenColor = add(new ColorSetting("Broken color", 0xFFEF4444));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.35, 0.1, 0.9, 0.05));

	/** Entity id -> world time when its shield works again. */
	private final Map<Integer, Long> brokenUntil = new HashMap<>();
	private World trackedWorld;

	public ShieldColor() {
		super("ShieldColor", "Green shield while it can block, red while an axe has disabled it", Category.VISUALS);
	}

	/** ClientPlayNetworkHandlerMixin: the shield of this entity was disabled (or broke). */
	public void onShieldBreak(Entity entity) {
		if (entity == mc.player || !(entity instanceof LivingEntity)) return;
		syncWorld();
		brokenUntil.put(entity.getId(), entity.getWorld().getTime() + DISABLE_TICKS);
	}

	/** ItemModelManagerMixin: overlay coordinates for this stack held by {@code holder}, or -1 to keep vanilla. */
	public int overlayFor(ItemStack stack, LivingEntity holder) {
		if (!isEnabled() || holder == null || !(stack.getItem() instanceof ShieldItem)) return -1;
		boolean broken;
		if (holder == mc.player) {
			if (!ownShield.get()) return -1;
			broken = mc.player.getItemCooldownManager().isCoolingDown(stack);
		} else {
			if (!otherShields.get()) return -1;
			syncWorld();
			Long until = brokenUntil.get(holder.getId());
			long now = holder.getWorld().getTime();
			// blocking again a bit after the hit: the cooldown is over (or was shorter than an axe's)
			if (until != null && (now >= until || (holder.isBlocking() && now > until - DISABLE_TICKS + 10))) {
				brokenUntil.remove(holder.getId());
				until = null;
			}
			broken = until != null;
		}
		return OverlayTexture.packUv(0, broken ? BROKEN_ROW : READY_ROW);
	}

	/** OverlayTextureMixin: the overlay pixel of one row. Its alpha is how much of the shield texture stays. */
	public int rowColor(boolean broken) {
		return ColorUtil.withAlpha(broken ? brokenColor.get() : readyColor.get(), 1 - opacity.getFloat());
	}

	/** Entity ids start over in a new world. */
	private void syncWorld() {
		if (mc.world != trackedWorld) {
			trackedWorld = mc.world;
			brokenUntil.clear();
		}
	}
}
