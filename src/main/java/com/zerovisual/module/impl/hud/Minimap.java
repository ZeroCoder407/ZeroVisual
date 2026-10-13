package com.zerovisual.module.impl.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.waypoint.WaypointManager;
import com.zerovisual.waypoint.WaypointManager.Waypoint;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

/**
 * Terrain map of the chunks your client has already loaded, with your waypoints.
 * Other players and mobs are deliberately not shown (that would be a radar).
 */
public class Minimap extends HudModule {
	private static final int RES = 128;
	private static final Identifier TEXTURE = Identifier.of("zerovisual", "minimap");

	public final NumberSetting size = add(new NumberSetting("Size", 100, 60, 160, 5));
	public final ModeSetting zoom = add(new ModeSetting("Zoom", "Normal", "Near", "Normal", "Far"));
	public final ModeSetting shape = add(new ModeSetting("Shape", "Circle", "Circle", "Square"));
	public final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
	public final BooleanSetting coordinates = add(new BooleanSetting("Coordinates", true));
	public final BooleanSetting waypoints = add(new BooleanSetting("Waypoints", true));

	private NativeImageBackedTexture texture;
	private final BlockPos.Mutable pos = new BlockPos.Mutable();
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
		return switch (zoom.get()) {
			case "Near" -> 0.5f;
			case "Far" -> 2f;
			default -> 1f;
		};
	}

	/** Rotation of the map in degrees: the view direction points up when Rotate is on. */
	private float mapAngle(float tickDelta) {
		return rotate.get() ? mc.player.getYaw(tickDelta) - 180f : 0f;
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null) return;
		if (ticks++ % 2 == 0) updateImage();
	}

	private void updateImage() {
		if (texture == null) {
			texture = new NativeImageBackedTexture(RES, RES, true);
			mc.getTextureManager().registerTexture(TEXTURE, texture);
		}
		NativeImage image = texture.getImage();
		if (image == null) return;
		ClientWorld world = mc.world;
		double px = mc.player.getX(), pz = mc.player.getZ();
		float bpp = blocksPerPixel();
		double angle = Math.toRadians(mapAngle(1f));
		double cos = Math.cos(angle), sin = Math.sin(angle);
		boolean circle = shape.is("Circle");
		boolean ceiling = world.getDimension().hasCeiling();
		int startY = mc.player.getBlockY() + 1;
		float half = RES / 2f;

		for (int iy = 0; iy < RES; iy++) {
			for (int ix = 0; ix < RES; ix++) {
				float sx = ix + 0.5f - half, sy = iy + 0.5f - half;
				if (!insideMask(sx, sy, half, circle)) {
					image.setColorArgb(ix, iy, 0);
					continue;
				}
				// screen offset -> world offset (rotated clockwise by the map angle)
				double dx = (sx * cos - sy * sin) * bpp;
				double dz = (sx * sin + sy * cos) * bpp;
				int wx = MathHelper.floor(px + dx), wz = MathHelper.floor(pz + dz);
				image.setColorArgb(ix, iy, columnColor(world, wx, wz, ceiling, startY));
			}
		}
		texture.upload();
	}

	private static boolean insideMask(float sx, float sy, float half, boolean circle) {
		if (circle) return sx * sx + sy * sy <= half * half;
		float r = 12, ax = Math.abs(sx) - (half - r), ay = Math.abs(sy) - (half - r);
		return ax <= 0 || ay <= 0 || ax * ax + ay * ay <= r * r;
	}

	private int columnColor(ClientWorld world, int x, int z, boolean ceiling, int startY) {
		if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) return 0xFF101016;
		int y = surfaceY(world, x, z, ceiling, startY);
		if (y < world.getBottomY()) return 0xFF101016;
		pos.set(x, y, z);
		BlockState state = world.getBlockState(pos);
		MapColor color = state.getMapColor(world, pos);
		if (color == MapColor.CLEAR) return 0xFF101016;

		MapColor.Brightness brightness;
		FluidState fluid = state.getFluidState();
		if (fluid.isIn(FluidTags.WATER)) {
			// deeper water is darker, like vanilla maps
			int depth = 0;
			while (depth < 10 && world.getFluidState(pos.move(0, -1, 0)).isIn(FluidTags.WATER)) depth++;
			brightness = depth > 6 ? MapColor.Brightness.LOW : depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
		} else {
			int north = surfaceY(world, x, z - 1, ceiling, startY);
			brightness = north < y ? MapColor.Brightness.HIGH : north > y ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
		}
		return color.getRenderColor(brightness);
	}

	/** Top block of the column; under a ceiling (Nether) the first floor below the player. */
	private int surfaceY(ClientWorld world, int x, int z, boolean ceiling, int startY) {
		if (!ceiling) return world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
		BlockPos.Mutable p = new BlockPos.Mutable(x, startY, z);
		boolean air = false;
		for (int i = 0; i < 64 && p.getY() > world.getBottomY(); i++, p.move(0, -1, 0)) {
			boolean empty = world.getBlockState(p).isAir();
			if (air && !empty) return p.getY();
			air |= empty;
		}
		return world.getBottomY() - 1;
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		if (mc.player == null || texture == null) return;
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
		ctx.drawTexture(RenderLayer::getGuiTextured, TEXTURE, 0, 0, 0, 0, RES, RES, RES, RES);
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
			double px = mc.player.getLerpedPos(tickDelta).x, pz = mc.player.getLerpedPos(tickDelta).z;
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
					sx = MathHelper.clamp(sx, -max, max);
					sy = MathHelper.clamp(sy, -max, max);
				}
				Render2D.circle(ctx, cx + sx, cy + sy, 3.2f, 0xE00C0C12);
				Render2D.circle(ctx, cx + sx, cy + sy, 2.2f, w.color);
			}
		}

		drawArrow(ctx, cx, cy, rotate.get() ? 0 : mc.player.getYaw(tickDelta) - 180f);

		if (coordinates.get()) {
			String coords = mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
			Render2D.centeredText(ctx, coords, cx, y + s + 3, Theme.TEXT_MUTED);
		}
	}

	private void drawArrow(DrawContext ctx, float cx, float cy, float degrees) {
		ctx.draw();
		ctx.getMatrices().push();
		ctx.getMatrices().translate(cx, cy, 0);
		ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
		Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
		BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
		arrow(b, m, 1.6f, 0xFF0C0C12);
		arrow(b, m, 1f, 0xFFFFFFFF);
		BufferRenderer.drawWithGlobalProgram(b.end());
		RenderSystem.disableBlend();
		ctx.getMatrices().pop();
	}

	private static void arrow(BufferBuilder b, Matrix4f m, float k, int color) {
		float tipY = -5 * k, baseY = 4 * k, notchY = 1.5f * k, side = 3.6f * k;
		b.vertex(m, 0, tipY, 0).color(color);
		b.vertex(m, -side, baseY, 0).color(color);
		b.vertex(m, 0, notchY, 0).color(color);
		b.vertex(m, 0, tipY, 0).color(color);
		b.vertex(m, 0, notchY, 0).color(color);
		b.vertex(m, side, baseY, 0).color(color);
	}
}
