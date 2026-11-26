package com.zerovisual.mixin;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.visuals.ItemZoom;
import com.zerovisual.module.impl.visuals.SmoothSwap;
import com.zerovisual.render.DrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SmoothSwap (items glide between slots) and ItemZoom (the hovered item grows around the slot center). */
@Mixin(GuiContainer.class)
public abstract class GuiContainerMixin extends GuiScreen {
	@Shadow
	protected int guiLeft;
	@Shadow
	protected int guiTop;
	@Shadow
	public Container inventorySlots;

	@Inject(method = "drawScreen", at = @At("HEAD"))
	private void zerovisual$swapUpdate(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		SmoothSwap swap = ZeroVisualClient.get().modules().get(SmoothSwap.class);
		if (swap.isEnabled()) swap.update(inventorySlots);
	}

	/** After the slots, still in the coordinates of the container. */
	@Inject(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerForegroundLayer(II)V"))
	private void zerovisual$swapRender(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		SmoothSwap swap = ZeroVisualClient.get().modules().get(SmoothSwap.class);
		if (swap.isEnabled()) swap.render(new DrawContext());
	}

	@Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
	private void zerovisual$slotBegin(Slot slot, CallbackInfo ci) {
		SmoothSwap swap = ZeroVisualClient.get().modules().get(SmoothSwap.class);
		if (swap.isEnabled() && swap.hides(slot)) {
			ci.cancel();
			return;
		}
		GlStateManager.pushMatrix();
		ItemZoom zoom = ZeroVisualClient.get().modules().get(ItemZoom.class);
		if (!zoom.isEnabled()) return;
		Minecraft mc = Minecraft.getMinecraft();
		double mx = Mouse.getX() * width / (double) mc.displayWidth - guiLeft;
		double my = height - Mouse.getY() * height / (double) mc.displayHeight - 1 - guiTop;
		boolean hovered = slot.getHasStack() && mx >= slot.xDisplayPosition - 1 && mx < slot.xDisplayPosition + 17
			&& my >= slot.yDisplayPosition - 1 && my < slot.yDisplayPosition + 17;
		float scale = zoom.scaleFor(slot, hovered);
		if (scale <= 1.001f) return;
		// grows around the slot center, above its neighbours
		float cx = slot.xDisplayPosition + 8, cy = slot.yDisplayPosition + 8;
		GlStateManager.translate(cx, cy, 50);
		GlStateManager.scale(scale, scale, 1);
		GlStateManager.translate(-cx, -cy, 0);
	}

	@Inject(method = "drawSlot", at = @At("RETURN"))
	private void zerovisual$slotEnd(Slot slot, CallbackInfo ci) {
		GlStateManager.popMatrix();
	}
}
