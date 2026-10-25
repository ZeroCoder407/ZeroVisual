package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.hud.Potions;
import com.zerovisual.module.impl.hud.Saturation;
import com.zerovisual.module.impl.visuals.Crosshair;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class InGameHudMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	protected abstract boolean canRenderCrosshairForSpectator(@Nullable HitResult hitResult);

	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void zerovisual$hideVanillaEffects(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
		Potions potions = ZeroVisualClient.get().modules().get(Potions.class);
		if (potions.isEnabled() && potions.hideVanilla.get()) ci.cancel();
	}

	@Inject(method = "extractFood", at = @At("TAIL"))
	private void zerovisual$saturation(GuiGraphicsExtractor context, Player player, int top, int right, CallbackInfo ci) {
		Saturation saturation = ZeroVisualClient.get().modules().get(Saturation.class);
		if (saturation.isEnabled()) saturation.draw(context, player, top, right);
	}

	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void zerovisual$customCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
		Crosshair crosshair = ZeroVisualClient.get().modules().get(Crosshair.class);
		// keep the vanilla 3D axes crosshair of the debug screen
		if (!crosshair.isEnabled() || minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)) return;
		ci.cancel();
		if (!minecraft.options.getCameraType().isFirstPerson()) return;
		if (minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR && !canRenderCrosshairForSpectator(minecraft.hitResult)) return;
		crosshair.draw(context);
	}
}
