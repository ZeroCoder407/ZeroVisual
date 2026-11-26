package com.zerovisual.module.impl.hud;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.DrawContext;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.waypoint.WaypointManager;
import com.zerovisual.waypoint.WaypointManager.Waypoint;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.opengl.GL11;

/**
 * Terrain map of the chunks your client has already loaded, with your waypoints.
 * Other players and mobs are deliberately not shown (that would be a radar).
 */
public class Minimap extends HudModule {
	private static final int RES = 128;

	public final NumberSetting size = add(new NumberSetting("Size", 100, 60, 160, 5));
	public final ModeSetting zoom = add(new ModeSetting("Zoom", "Normal", "Near", "Normal", "Far"));
	public final ModeSetting shape = add(new ModeSetting("Shape", "Circle", "Circle", "Square"));
	public final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
	public final BooleanSetting coordinates = add(new BooleanSetting("Coordinates", true));
	public final BooleanSetting waypoints = add(new BooleanSetting("Waypoints", true));

	private DynamicTexture texture;
	private ResourceLocation textureId;
	private int ticks;

	public Minimap() {
		super("Minimap", "Map of the loaded terrain around you with your waypoints");
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		// bottom right: the module list and notifications use the top right corner
		return new float[]{screenW - w - 6, screenH - h - 6};
	}

	private float blocksPerPixel() {
		return zoom.is("Near") ? 0.5f : zoom.is("Far") ? 2f : 1f;
	}

	private float yaw(float tickDelta) {
		return mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * tickDelta;
	}

	/** Rotation of the map in degrees: the view direction points up when Rotate is on. */
	private float mapAngle(float tickDelta) {
		return rotate.get() ? yaw(tickDelta) - 180f : 0f;
	}

	@Override
	public void onTick() {
		if (mc.thePlayer == null || mc.theWorld == null) return;
		if (ticks++ % 2 == 0) updateImage();
	}

	private void updateImage() {
		if (texture == null) {
			texture = new DynamicTexture(RES, RES);
			textureId = mc.getTextureManager().getDynamicTextureLocation("zerovisual_minimap", texture);
		}
		int[] image = texture.getTextureData();
		World world = mc.theWorld;
		double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
		float bpp = blocksPerPixel();
		double angle = Math.toRadians(mapAngle(1f));
		double cos = Math.cos(angle), sin = Math.sin(angle);
		boolean circle = shape.is("Circle");
		boolean ceiling = world.provider.getHasNoSky();
		int startY = MathHelper.floor_double(mc.thePlayer.posY) + 1;
		float half = RES / 2f;

		for (int iy = 0; iy < RES; iy++) {
			for (int ix = 0; ix < RES; ix++) {
				float sx = ix + 0.5f - half, sy = iy + 0.5f - half;
				if (!insideMask(sx, sy, half, circle)) {
					image[iy * RES + ix] = 0;
					continue;
				}
				// screen offset -> world offset (rotated clockwise by the map angle)
				double dx = (sx * cos - sy * sin) * bpp;
				double dz = (sx * sin + sy * cos) * bpp;
				int wx = MathHelper.floor_double(px + dx), wz = MathHelper.floor_double(pz + dz);
				image[iy * RES + ix] = columnColor(world, wx, wz, ceiling, startY);
			}
		}
		texture.updateDynamicTexture();
	}

	private static boolean insideMask(float sx, float sy, float half, boolean circle) {
		if (circle) return sx * sx + sy * sy <= half * half;
		float r = 12, ax = Math.abs(sx) - (half - r), ay = Math.abs(sy) - (half - r);
		return ax <= 0 || ay <= 0 || ax * ax + ay * ay <= r * r;
	}

	private static final int LOW = 180, NORMAL = 220, HIGH = 255;

	private int columnColor(World world, int x, int z, boolean ceiling, int startY) {
		Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
		if (chunk.isEmpty()) return 0xFF101016;
		int y = surfaceY(world, chunk, x, z, ceiling, startY);
		if (y < 0) return 0xFF101016;
		BlockPos pos = new BlockPos(x, y, z);
		IBlockState state = world.getBlockState(pos);
		MapColor color = state.getBlock().getMapColor(state);
		if (color == MapColor.airColor) return 0xFF101016;

		int brightness;
		if (state.getBlock().getMaterial() == Material.water) {
			// deeper water is darker, like vanilla maps
			int depth = 0;
			while (depth < 10 && world.getBlockState(pos.down(depth + 1)).getBlock().getMaterial() == Material.water) depth++;
			brightness = depth > 6 ? LOW : depth > 2 ? NORMAL : HIGH;
		} else {
			Chunk northChunk = world.getChunkFromChunkCoords(x >> 4, (z - 1) >> 4);
			int north = surfaceY(world, northChunk, x, z - 1, ceiling, startY);
			brightness = north < y ? HIGH : north > y ? LOW : NORMAL;
		}
		return shade(color.colorValue, brightness);
	}

	/** The map color at one of the brightness steps of vanilla maps. */
	private static int shade(int rgb, int brightness) {
		int r = (rgb >> 16 & 255) * brightness / 255, g = (rgb >> 8 & 255) * brightness / 255, b = (rgb & 255) * brightness / 255;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** Top block of the column; under a ceiling (Nether) the first floor below the player. */
	private int surfaceY(World world, Chunk chunk, int x, int z, boolean ceiling, int startY) {
		if (!ceiling) {
			// the height map skips see-through blocks: walk down to the first block with a map color
			int y = chunk.getHeightValue(x & 15, z & 15);
			for (int i = 0; i < 8 && y > 0; i++) {
				IBlockState state = chunk.getBlockState(new BlockPos(x, y, z));
				if (state.getBlock().getMapColor(state) != MapColor.airColor) return y;
				y--;
			}
			return y;
		}
		boolean air = false;
		for (int y = startY, i = 0; i < 64 && y > 0; i++, y--) {
			boolean empty = world.isAirBlock(new BlockPos(x, y, z));
			if (air && !empty) return y;
			air |= empty;
		}
		return -1;
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		if (mc.thePlayer == null || texture == null) return;
		float s = size.getFloat();
		float textH = coordinates.get() ? 12 : 0;
		float[] p = place(ctx, s, s + textH);
		float x = p[0], y = p[1];
		float cx = x + s / 2, cy = y + s / 2;
		boolean circle = shape.is("Circle");
		float radius = circle ? s / 2 : s * 12 / RES;
		int accent = Theme.accent();

		Render2D.shadow(ctx, x, y + 2, s, s, radius, 10, 0x90000000);
		Render2D.rect(ctx, x, y, s, s, radius, 0xFF101016);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 0);
		ctx.getMatrices().scale(s / RES, s / RES, 1);
		ctx.drawTexture(textureId, 0, 0, 0, 0, RES, RES, RES, RES);
		ctx.getMatrices().pop();
		Render2D.outline(ctx, x, y, s, s, radius, 1.5f, ColorUtil.withAlpha(accent, 0.8f));

		float angle = mapAngle(tickDelta);
		float scale = s / RES / blocksPerPixel();
		float edge = s / 2 - 5;

		// north marker on the edge
		double north = Math.toRadians(-angle);
		float nx = cx + (float) Math.sin(north) * edge, ny = cy - (float) Math.cos(north) * edge;
		Render2D.circle(ctx, nx, ny, 5, 0xE00C0C12);
		Render2D.centeredText(ctx, "N", nx + 0.5f, ny - 3.5f, 0xFFF43F5E);

		if (waypoints.get()) {
			String dimension = WaypointManager.currentDimension();
			double rad = Math.toRadians(-angle), cos = Math.cos(rad), sin = Math.sin(rad);
			double px = mc.thePlayer.prevPosX + (mc.thePlayer.posX - mc.thePlayer.prevPosX) * tickDelta;
			double pz = mc.thePlayer.prevPosZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * tickDelta;
			for (Waypoint w : ZeroVisualClient.get().waypoints().current()) {
				if (!w.visible || !w.dimension.equals(dimension)) continue;
				double dx = w.x + 0.5 - px, dz = w.z + 0.5 - pz;
				// world offset -> screen offset (inverse rotation)
				float sx = (float) (dx * cos - dz * sin) * scale, sy = (float) (dx * sin + dz * cos) * scale;
				float dist = (float) Math.sqrt(sx * sx + sy * sy);
				float max = circle ? edge : s / 2 - 4;
				if (circle && dist > max) {
					sx *= max / dist;
					sy *= max / dist;
				} else if (!circle) {
					sx = MathHelper.clamp_float(sx, -max, max);
					sy = MathHelper.clamp_float(sy, -max, max);
				}
				Render2D.circle(ctx, cx + sx, cy + sy, 3.2f, 0xE00C0C12);
				Render2D.circle(ctx, cx + sx, cy + sy, 2.2f, w.color);
			}
		}

		drawArrow(ctx, cx, cy, rotate.get() ? 0 : yaw(tickDelta) - 180f);

		if (coordinates.get()) {
			BlockPos at = new BlockPos(mc.thePlayer);
			String coords = at.getX() + ", " + at.getY() + ", " + at.getZ();
			Render2D.centeredText(ctx, coords, cx, y + s + 3, Theme.TEXT_MUTED);
		}
	}

	private void drawArrow(DrawContext ctx, float cx, float cy, float degrees) {
		GlStateManager.pushMatrix();
		GlStateManager.translate(cx, cy, 0);
		GlStateManager.rotate(degrees, 0, 0, 1);
		GlStateManager.enableBlend();
		GlStateManager.disableTexture2D();
		GlStateManager.disableCull();
		WorldRenderer b = Tessellator.getInstance().getWorldRenderer();
		b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
		arrow(b, 1.6f, 0xFF0C0C12);
		arrow(b, 1f, 0xFFFFFFFF);
		Tessellator.getInstance().draw();
		GlStateManager.enableCull();
		GlStateManager.enableTexture2D();
		GlStateManager.popMatrix();
	}

	private static void arrow(WorldRenderer b, float k, int color) {
		float tipY = -5 * k, baseY = 4 * k, notchY = 1.5f * k, side = 3.6f * k;
		int r = color >> 16 & 255, g = color >> 8 & 255, bl = color & 255, a = color >>> 24;
		b.pos(0, tipY, 0).color(r, g, bl, a).endVertex();
		b.pos(-side, baseY, 0).color(r, g, bl, a).endVertex();
		b.pos(0, notchY, 0).color(r, g, bl, a).endVertex();
		b.pos(0, tipY, 0).color(r, g, bl, a).endVertex();
		b.pos(0, notchY, 0).color(r, g, bl, a).endVertex();
		b.pos(side, baseY, 0).color(r, g, bl, a).endVertex();
	}
}
