package com.zerovisual.mixin;

import com.zerovisual.pwf.PwfManager;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Play with friends needs the LAN port, also when the world was opened with the vanilla "Open to LAN". */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
	@Inject(method = "shareToLAN", at = @At("RETURN"))
	private void zerovisual$lanPort(WorldSettings.GameType type, boolean allowCheats, CallbackInfoReturnable<String> cir) {
		try {
			PwfManager.setLanPort(Integer.parseInt(cir.getReturnValue()));
		} catch (NumberFormatException ignored) {
			// opening failed: vanilla returns null
		}
	}
}
