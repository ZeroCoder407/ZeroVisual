package com.zerovisual.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/** The living entity you hit last, fed by the attack callback. Used by TargetHUD and TargetESP. */
public final class TargetTracker {
	private static EntityLivingBase target;
	private static long lastAttack;

	private TargetTracker() {
	}

	public static void onAttack(Entity entity) {
		if (entity instanceof EntityLivingBase) {
			target = (EntityLivingBase) entity;
			lastAttack = System.currentTimeMillis();
		}
	}

	/** The last hit entity, dead or alive, if it was hit within {@code keepMs} (for kill effects). */
	public static EntityLivingBase lastAttacked(long keepMs) {
		if (target == null || System.currentTimeMillis() - lastAttack > keepMs) return null;
		return target;
	}

	/** The last hit entity if it is still alive, near and was hit within {@code keepMs}. */
	public static EntityLivingBase get(long keepMs) {
		Minecraft mc = Minecraft.getMinecraft();
		if (target == null || mc.thePlayer == null) return null;
		if (!target.isEntityAlive() || target.isDead || target.worldObj != mc.theWorld
			|| System.currentTimeMillis() - lastAttack > keepMs
			|| target.getDistanceSqToEntity(mc.thePlayer) > 32 * 32) {
			return null;
		}
		return target;
	}
}
