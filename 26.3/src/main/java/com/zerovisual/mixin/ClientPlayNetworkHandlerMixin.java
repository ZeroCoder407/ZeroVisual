package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.cosmetics.TotemPop;
import com.zerovisual.module.impl.visuals.ShieldColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
	@Shadow
	private ClientLevel level;

	/** After the packet was moved to the main thread: count totem pops for TotemPop. */
	@Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void zerovisual$totemPop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		if (packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH || level == null) return;
		Entity entity = packet.getEntity(level);
		if (entity != null) ZeroVisualClient.get().modules().get(TotemPop.class).onPop(entity);
	}

	/**
	 * A disabled shield has no entity event any more, only the break sound at the feet of its holder:
	 * the closest living entity to that spot is the one for ShieldColor.
	 */
	@Inject(method = "handleSoundEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void zerovisual$shieldBreak(ClientboundSoundPacket packet, CallbackInfo ci) {
		if (level == null || packet.getSound().value() != SoundEvents.SHIELD_BREAK.value()) return;
		double x = packet.getX(), y = packet.getY(), z = packet.getZ();
		LivingEntity closest = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1))) {
			double d = e.distanceToSqr(x, y, z);
			if (d < best) {
				best = d;
				closest = e;
			}
		}
		if (closest != null) ZeroVisualClient.get().modules().get(ShieldColor.class).onShieldBreak(closest);
	}
}
