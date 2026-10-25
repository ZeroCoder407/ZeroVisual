package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.ItemZoom;
import com.zerovisual.module.impl.visuals.SmoothSwap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SmoothSwap (items glide between slots) and ItemZoom (the hovered item grows). */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> {
	@Shadow
	protected Slot focusedSlot;
	@Shadow
	@Final
	protected T handler;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlots(Lnet/minecraft/client/gui/DrawContext;)V"))
	private void zerovisual$swapUpdate(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SmoothSwap swap = ZeroVisualClient.get().modules().get(SmoothSwap.class);
		if (swap.isEnabled()) swap.update(handler);
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlots(Lnet/minecraft/client/gui/DrawContext;)V", shift = At.Shift.AFTER))
	private void zerovisual$swapRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SmoothSwap swap = ZeroVisualClient.get().modules().get(SmoothSwap.class);
		if (swap.isEnabled()) swap.render(context);
	}

	@Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
	private void zerovisual$slotBegin(DrawContext context, Slot slot, CallbackInfo ci) {
		var modules = ZeroVisualClient.get().modules();
		SmoothSwap swap = modules.get(SmoothSwap.class);
		if (swap.isEnabled() && swap.hides(slot)) {
			ci.cancel();
			return;
		}
		context.getMatrices().push();
		ItemZoom zoom = modules.get(ItemZoom.class);
		if (!zoom.isEnabled()) return;
		float s = zoom.scaleFor(slot, slot == focusedSlot && slot.hasStack());
		if (s <= 1.001f) return;
		// grows around the slot center, above its neighbours
		context.getMatrices().translate(slot.x + 8, slot.y + 8, 50);
		context.getMatrices().scale(s, s, 1);
		context.getMatrices().translate(-(slot.x + 8), -(slot.y + 8), 0);
	}

	@Inject(method = "drawSlot", at = @At("RETURN"))
	private void zerovisual$slotEnd(DrawContext context, Slot slot, CallbackInfo ci) {
		context.getMatrices().pop();
	}
}
