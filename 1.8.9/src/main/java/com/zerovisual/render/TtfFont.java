package com.zerovisual.render;

import com.zerovisual.ZeroVisualClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The TTF fonts of the menu, which 1.8.9 cannot load itself. Glyphs are rendered with AWT at the pixel size of the
 * current GUI scale, so they land on whole screen pixels, and kept in texture pages. Metrics match the vanilla font:
 * 9 units high, baseline at 7.5 (the newer versions shift the TTF fonts by half a unit).
 */
public final class TtfFont {
	private static final Map<String, TtfFont> FONTS = new HashMap<String, TtfFont>();
	private static final int PAGE = 1024;
	private static final float SIZE = 9, BASELINE = 7.5f;
	/** Vanilla § colors 0..f. */
	private static final int[] FORMAT_COLORS = new int[16];

	static {
		for (int i = 0; i < 16; i++) {
			int bright = (i >> 3 & 1) * 85;
			int r = (i >> 2 & 1) * 170 + bright, g = (i >> 1 & 1) * 170 + bright, b = (i & 1) * 170 + bright;
			if (i == 6) r += 85;
			FORMAT_COLORS[i] = r << 16 | g << 8 | b;
		}
	}

	private final Font[] fonts;
	private final Map<Integer, Cache> caches = new HashMap<Integer, Cache>();

	private TtfFont(Font[] fonts) {
		this.fonts = fonts;
	}

	/** The font {@code assets/zerovisual/font/<name>_latin.ttf} plus its Cyrillic file, or null if it cannot be read. */
	public static TtfFont get(String name) {
		if (FONTS.containsKey(name)) return FONTS.get(name);
		TtfFont font = null;
		try {
			font = new TtfFont(new Font[]{load(name + "_latin"), load(name + "_cyrillic"), new Font(Font.DIALOG, Font.PLAIN, 1)});
		} catch (Exception e) {
			ZeroVisualClient.LOGGER.error("Font {} could not be loaded", name, e);
		}
		FONTS.put(name, font);
		return font;
	}

	private static Font load(String file) throws Exception {
		ResourceLocation location = new ResourceLocation(ZeroVisualClient.MOD_ID, "font/" + file + ".ttf");
		InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();
		try {
			return Font.createFont(Font.TRUETYPE_FONT, in);
		} finally {
			in.close();
		}
	}

	private Cache cache() {
		int scale = Math.max(1, new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor());
		Cache cache = caches.get(scale);
		if (cache == null) {
			cache = new Cache(scale);
			caches.put(scale, cache);
		}
		return cache;
	}

	public float width(String text) {
		Cache cache = cache();
		float width = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				i++;
				continue;
			}
			width += cache.glyph(c).advance;
		}
		return width;
	}

	public void draw(String text, float x, float y, int color, boolean shadow) {
		if ((color & 0xFC000000) == 0) color |= 0xFF000000;
		if (shadow) draw(text, x + 1, y + 1, (color & 0xFCFCFC) >> 2 | color & 0xFF000000, true, false);
		draw(text, x, y, color, false, true);
	}

	private void draw(String text, float x, float y, int color, boolean isShadow, boolean keepFormat) {
		Cache cache = cache();
		float alpha = (color >>> 24) / 255f;
		int rgb = color & 0xFFFFFF;
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.color(1, 1, 1, 1);
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer buffer = tessellator.getWorldRenderer();
		int page = -1;
		float pen = x, baseline = y + BASELINE;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				int format = "0123456789abcdef".indexOf(Character.toLowerCase(text.charAt(++i)));
				if (format >= 0) rgb = isShadow ? (FORMAT_COLORS[format] & 0xFCFCFC) >> 2 : FORMAT_COLORS[format];
				else if (Character.toLowerCase(text.charAt(i)) == 'r') rgb = color & 0xFFFFFF;
				continue;
			}
			Glyph g = cache.glyph(c);
			if (g.width > 0) {
				if (g.page != page) {
					if (page >= 0) tessellator.draw();
					page = g.page;
					GlStateManager.bindTexture(cache.pages.get(page).texture);
					buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
				}
				float r = (rgb >> 16 & 255) / 255f, gr = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
				float x0 = pen + g.offsetX, y0 = baseline + g.offsetY, x1 = x0 + g.width, y1 = y0 + g.height;
				buffer.pos(x0, y0, 0).tex(g.u0, g.v0).color(r, gr, b, alpha).endVertex();
				buffer.pos(x0, y1, 0).tex(g.u0, g.v1).color(r, gr, b, alpha).endVertex();
				buffer.pos(x1, y1, 0).tex(g.u1, g.v1).color(r, gr, b, alpha).endVertex();
				buffer.pos(x1, y0, 0).tex(g.u1, g.v0).color(r, gr, b, alpha).endVertex();
			}
			pen += g.advance;
		}
		if (page >= 0) tessellator.draw();
	}

	private static final class Glyph {
		int page;
		float u0, v0, u1, v1, offsetX, offsetY, width, height, advance;
	}

	private static final class Page {
		final int texture = TextureUtil.glGenTextures();
		final BufferedImage image = new BufferedImage(PAGE, PAGE, BufferedImage.TYPE_INT_ARGB);
		int x = 1, y = 1, rowHeight;

		Page() {
			TextureUtil.allocateTexture(texture, PAGE, PAGE);
		}
	}

	/** The glyphs at one pixel size. */
	private final class Cache {
		final int scale;
		final Font[] sized = new Font[fonts.length];
		final FontRenderContext context = new FontRenderContext(null, true, true);
		final Map<Character, Glyph> glyphs = new HashMap<Character, Glyph>();
		final List<Page> pages = new ArrayList<Page>();

		Cache(int scale) {
			this.scale = scale;
			for (int i = 0; i < fonts.length; i++) sized[i] = fonts[i].deriveFont(SIZE * scale);
		}

		Glyph glyph(char c) {
			Glyph glyph = glyphs.get(c);
			if (glyph == null) {
				glyph = render(c);
				glyphs.put(c, glyph);
			}
			return glyph;
		}

		private Glyph render(char c) {
			Font font = sized[sized.length - 1];
			for (Font f : sized) {
				if (f.canDisplay(c)) {
					font = f;
					break;
				}
			}
			GlyphVector vector = font.createGlyphVector(context, String.valueOf(c));
			Glyph glyph = new Glyph();
			glyph.advance = vector.getGlyphMetrics(0).getAdvance() / scale;
			Rectangle bounds = vector.getPixelBounds(context, 0, 0);
			if (bounds.width <= 0 || bounds.height <= 0) return glyph;
			int w = bounds.width + 2, h = bounds.height + 2;
			Page page = pages.isEmpty() ? null : pages.get(pages.size() - 1);
			if (page != null && page.x + w > PAGE) {
				page.x = 1;
				page.y += page.rowHeight + 1;
				page.rowHeight = 0;
			}
			if (page == null || page.y + h > PAGE) {
				page = new Page();
				pages.add(page);
			}
			Graphics2D g = page.image.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(java.awt.Color.WHITE);
			g.drawGlyphVector(vector, page.x + 1 - bounds.x, page.y + 1 - bounds.y);
			g.dispose();
			BufferedImage region = page.image.getSubimage(page.x, page.y, w, h);
			TextureUtil.uploadTextureImageSub(page.texture, region, page.x, page.y, true, false);

			glyph.page = pages.indexOf(page);
			glyph.u0 = page.x / (float) PAGE;
			glyph.v0 = page.y / (float) PAGE;
			glyph.u1 = (page.x + w) / (float) PAGE;
			glyph.v1 = (page.y + h) / (float) PAGE;
			glyph.offsetX = (bounds.x - 1) / (float) scale;
			glyph.offsetY = (bounds.y - 1) / (float) scale;
			glyph.width = w / (float) scale;
			glyph.height = h / (float) scale;
			page.x += w + 1;
			page.rowHeight = Math.max(page.rowHeight, h);
			return glyph;
		}
	}
}
