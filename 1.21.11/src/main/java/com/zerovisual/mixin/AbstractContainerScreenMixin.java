package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.ItemZoom;
import com.zerovisual.module.impl.visuals.SmoothSwap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SmoothSwap (items glide between slots) and ItemZoom (the hovered item grows). */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {
	@Shadow
	protected @Nullable Slot hoveredSlot;
	@Shadow
	@Final
	protected T menu;

	@Inject(method = "renderContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlots(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
	private void zerovisual$swapUpdate(GuiGraphics graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		SmoothSwap swap = ZeroVisualClient.get().modules().get(SmoothSwap.class);
		if (swap.isEnabled()) swap.update(menu);
	}

	@Inject(method = "renderContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlots(Lnet/minecraft/client/gui/GuiGraphics;II)V", shift = At.Shift.AFTER))
	private void zerovisual$swapRender(GuiGraphics graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		SmoothSwap swap = ZeroVisualClient.get().modules().get(SmoothSwap.class);
		if (swap.isEnabled()) swap.render(graphics);
	}

	@Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
	private void zerovisual$slotBegin(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		var modules = ZeroVisualClient.get().modules();
		SmoothSwap swap = modules.get(SmoothSwap.class);
		if (swap.isEnabled() && swap.hides(slot)) {
			ci.cancel();
			return;
		}
		graphics.pose().pushMatrix();
		ItemZoom zoom = modules.get(ItemZoom.class);
		if (!zoom.isEnabled()) return;
		float s = zoom.scaleFor(slot, slot == hoveredSlot && slot.hasItem());
		if (s <= 1.001f) return;
		// grows around the slot center
		graphics.pose().translate(slot.x + 8, slot.y + 8);
		graphics.pose().scale(s, s);
		graphics.pose().translate(-(slot.x + 8), -(slot.y + 8));
	}

	@Inject(method = "renderSlot", at = @At("RETURN"))
	private void zerovisual$slotEnd(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		graphics.pose().popMatrix();
	}
}
