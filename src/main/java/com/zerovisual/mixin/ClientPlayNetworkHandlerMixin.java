package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.cosmetics.TotemPop;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
	@Shadow
	private ClientWorld world;

	/** After the packet was moved to the main thread: count totem pops for TotemPop. */
	@Inject(method = "onEntityStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V", shift = At.Shift.AFTER))
	private void zerovisual$totemPop(EntityStatusS2CPacket packet, CallbackInfo ci) {
		if (packet.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING || world == null) return;
		Entity entity = packet.getEntity(world);
		if (entity != null) ZeroVisualClient.get().modules().get(TotemPop.class).onPop(entity);
	}
}
