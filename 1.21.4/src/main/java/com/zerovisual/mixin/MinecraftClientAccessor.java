package com.zerovisual.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
	@Mutable
	@Accessor("session")
	void zerovisual$setSession(Session session);

	@Mutable
	@Accessor("profileKeys")
	void zerovisual$setProfileKeys(ProfileKeys profileKeys);
}
