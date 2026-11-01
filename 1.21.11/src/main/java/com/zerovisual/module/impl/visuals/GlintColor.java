package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Recolors the enchantment glint. The glint textures are replaced with tinted copies: every pixel of the purple
 * vanilla texture is multiplied by the wanted color divided by the texture's own color.
 * Turning the module off gives the textures back to the game.
 */
public class GlintColor extends Module {
	/** Brightest channels of the vanilla glint texture (157, 80, 249), normalized. */
	private static final float TEX_R = 157 / 249f, TEX_G = 80 / 249f, TEX_B = 1f;
	private static final Identifier[] TEXTURES = {ItemRenderer.ENCHANTED_GLINT_ITEM, ItemRenderer.ENCHANTED_GLINT_ARMOR};

	public final BooleanSetting useAccent = add(new BooleanSetting("Accent color", false));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF38BDF8));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1, 0.3, 2, 0.1));
	public final BooleanSetting rainbow = add(new BooleanSetting("Rainbow", false));

	/** Untinted pixels of the vanilla textures, read once. */
	private final Map<Identifier, NativeImage> originals = new HashMap<>();
	private final Map<Identifier, TintedTexture> tinted = new HashMap<>();
	private int appliedColor;
	private float appliedBrightness;

	public GlintColor() {
		super("GlintColor", "Custom color of the enchantment glint", Category.VISUALS);
	}

	@Override
	public void onTick() {
		if (mc.level == null) return;
		int c;
		if (rainbow.get()) c = java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 4000) / 4000f, 0.8f, 1f);
		else c = useAccent.get() ? Theme.accent() : color.get();
		float k = brightness.getFloat();
		if (c == appliedColor && k == appliedBrightness && tinted.size() == TEXTURES.length) return;
		appliedColor = c;
		appliedBrightness = k;
		float r = ((c >> 16) & 0xFF) / 255f / TEX_R * k, g = ((c >> 8) & 0xFF) / 255f / TEX_G * k, b = (c & 0xFF) / 255f / TEX_B * k;
		for (Identifier id : TEXTURES) {
			NativeImage original = original(id);
			if (original == null) continue;
			TintedTexture texture = tinted.get(id);
			if (texture == null || mc.getTextureManager().getTexture(id) != texture) {
				texture = new TintedTexture(id, original.getWidth(), original.getHeight());
				mc.getTextureManager().register(id, texture);
				tinted.put(id, texture);
			}
			NativeImage pixels = texture.getPixels();
			for (int y = 0; y < original.getHeight(); y++) {
				for (int x = 0; x < original.getWidth(); x++) {
					int p = original.getPixel(x, y);
					int nr = Math.min(255, Math.round(((p >> 16) & 0xFF) * r));
					int ng = Math.min(255, Math.round(((p >> 8) & 0xFF) * g));
					int nb = Math.min(255, Math.round((p & 0xFF) * b));
					pixels.setPixel(x, y, (p & 0xFF000000) | nr << 16 | ng << 8 | nb);
				}
			}
			texture.upload();
		}
	}

	@Override
	protected void onDisable() {
		// the game loads the vanilla textures again on the next use
		for (Identifier id : tinted.keySet()) mc.getTextureManager().release(id);
		tinted.clear();
	}

	private NativeImage original(Identifier id) {
		NativeImage image = originals.get(id);
		if (image != null) return image;
		try (InputStream stream = mc.getResourceManager().open(id)) {
			image = NativeImage.read(stream);
			originals.put(id, image);
			return image;
		} catch (Exception e) {
			ZeroVisualClient.LOGGER.warn("Failed to read the glint texture {}", id, e);
			return null;
		}
	}

	/** Same filtering as the vanilla glint (smooth), the texture scrolls so it repeats. */
	private static final class TintedTexture extends DynamicTexture {
		TintedTexture(Identifier id, int width, int height) {
			super(id::toString, width, height, false);
			sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
		}
	}
}
