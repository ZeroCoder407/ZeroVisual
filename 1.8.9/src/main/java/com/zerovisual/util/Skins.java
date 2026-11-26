package com.zerovisual.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** The head of a skin, face and hat layer, as the newer versions' PlayerSkinDrawer draws it; tinted by the current GL color. */
public final class Skins {
	private Skins() {
	}

	public static void drawHead(ResourceLocation skin, int x, int y, int size) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
		GlStateManager.enableBlend();
		Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
		Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
	}
}
