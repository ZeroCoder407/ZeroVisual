package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.hud.Potions;
import com.zerovisual.module.impl.hud.Saturation;
import net.minecraft.entity.player.PlayerEntity;
import com.zerovisual.module.impl.visuals.Crosshair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	@Final
	private DebugHud debugHud;

	@Shadow
	protected abstract boolean shouldRenderSpectatorCrosshair(@Nullable HitResult hitResult);

	@Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
	private void zerovisual$hideVanillaEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		Potions potions = ZeroVisualClient.get().modules().get(Potions.class);
		if (potions.isEnabled() && potions.hideVanilla.get()) ci.cancel();
	}

	@Inject(method = "renderFood", at = @At("TAIL"))
	private void zerovisual$saturation(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
		Saturation saturation = ZeroVisualClient.get().modules().get(Saturation.class);
		if (saturation.isEnabled()) saturation.draw(context, player, top, right);
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void zerovisual$customCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		Crosshair crosshair = ZeroVisualClient.get().modules().get(Crosshair.class);
		// keep the vanilla 3D axes crosshair of the F3 screen
		if (!crosshair.isEnabled() || debugHud.shouldShowDebugHud()) return;
		ci.cancel();
		if (!client.options.getPerspective().isFirstPerson()) return;
		if (client.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR && !shouldRenderSpectatorCrosshair(client.crosshairTarget)) return;
		crosshair.draw(context);
	}
}
