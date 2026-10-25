package com.zerovisual.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** The living entity you hit last, fed by the attack callback. Used by TargetHUD and TargetESP. */
public final class TargetTracker {
	private static LivingEntity target;
	private static long lastAttack;

	private TargetTracker() {
	}

	public static void onAttack(Entity entity) {
		if (entity instanceof LivingEntity living) {
			target = living;
			lastAttack = System.currentTimeMillis();
		}
	}

	/** The last hit entity, dead or alive, if it was hit within {@code keepMs} (for kill effects). */
	@Nullable
	public static LivingEntity lastAttacked(long keepMs) {
		if (target == null || System.currentTimeMillis() - lastAttack > keepMs) return null;
		return target;
	}

	/** The last hit entity if it is still alive, near and was hit within {@code keepMs}. */
	@Nullable
	public static LivingEntity get(long keepMs) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (target == null || mc.player == null) return null;
		if (!target.isAlive() || target.isRemoved() || target.getWorld() != mc.world
			|| System.currentTimeMillis() - lastAttack > keepMs
			|| target.squaredDistanceTo(mc.player) > 32 * 32) {
			return null;
		}
		return target;
	}
}
