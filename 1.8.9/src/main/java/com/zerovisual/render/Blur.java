package com.zerovisual.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;

/** Blurs the world behind a menu with the vanilla post shader, while that menu is open. */
public final class Blur {
	private static final ResourceLocation SHADER = new ResourceLocation("shaders/post/blur.json");
	private static boolean ours;

	private Blur() {
	}

	/** Every frame while the menu is open: starts the blur once, and only over a world. */
	public static void apply() {
		Minecraft mc = Minecraft.getMinecraft();
		if (ours || mc.theWorld == null || !net.minecraft.client.renderer.OpenGlHelper.shadersSupported) return;
		// a shader of the game (spectating a creeper, Super Secret Settings) stays
		if (mc.entityRenderer.getShaderGroup() != null) return;
		mc.entityRenderer.loadShader(SHADER);
		ours = mc.entityRenderer.getShaderGroup() != null;
	}

	public static void stop() {
		if (!ours) return;
		ours = false;
		Minecraft mc = Minecraft.getMinecraft();
		ShaderGroup group = mc.entityRenderer.getShaderGroup();
		if (group != null && group.getShaderGroupName().equals(SHADER.toString())) mc.entityRenderer.stopUseShader();
	}
}
