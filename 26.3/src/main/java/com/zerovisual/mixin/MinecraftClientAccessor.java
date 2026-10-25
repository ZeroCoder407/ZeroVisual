package com.zerovisual.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
	@Mutable
	@Accessor("user")
	void zerovisual$setSession(User session);

	@Mutable
	@Accessor("profileKeyPairManager")
	void zerovisual$setProfileKeys(ProfileKeyPairManager profileKeys);
}
