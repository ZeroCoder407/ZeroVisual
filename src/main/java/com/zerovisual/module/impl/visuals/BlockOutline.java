package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

/** Colored outline and translucent fill of the targeted block, replacing the vanilla black lines. */
public class BlockOutline extends Module {
	public final BooleanSetting useAccent = add(new BooleanSetting("Accent color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF8B5CF6)
		.visibleWhen(() -> !useAccent.get()));
	public final NumberSetting lineWidth = add(new NumberSetting("Line width", 2, 1, 5, 0.5));
	public final BooleanSetting fill = add(new BooleanSetting("Fill", true));
	public final NumberSetting fillOpacity = add(new NumberSetting("Fill opacity", 0.15, 0.05, 0.6, 0.05)
		.visibleWhen(fill::get));

	public BlockOutline() {
		super("BlockOutline", "Colored outline and fill of the targeted block", Category.VISUALS);
	}

	/** @return false to cancel the vanilla outline */
	public boolean render(WorldRenderContext context, WorldRenderContext.BlockOutlineContext outline) {
		if (mc.world == null) return true;
		BlockPos pos = outline.blockPos();
		VoxelShape shape = outline.blockState().getOutlineShape(mc.world, pos, ShapeContext.of(outline.entity()));
		if (shape.isEmpty()) return true;

		int c = (useAccent.get() ? Theme.accent() : color.get()) | 0xFF000000;
		float ox = (float) (pos.getX() - outline.cameraX());
		float oy = (float) (pos.getY() - outline.cameraY());
		float oz = (float) (pos.getZ() - outline.cameraZ());
		Matrix4f m = new Matrix4f().translate(ox, oy, oz);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

		if (fill.get()) {
			int fc = ColorUtil.withAlpha(c, fillOpacity.getFloat());
			BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
			float e = 0.002f;
			shape.forEachBox((x1, y1, z1, x2, y2, z2) -> box(quads, m,
				(float) x1 - e, (float) y1 - e, (float) z1 - e, (float) x2 + e, (float) y2 + e, (float) z2 + e, fc));
			BuiltBuffer built = quads.endNullable();
			if (built != null) BufferRenderer.drawWithGlobalProgram(built);
		}

		RenderSystem.lineWidth(lineWidth.getFloat());
		BufferBuilder lines = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
		shape.forEachEdge((x1, y1, z1, x2, y2, z2) -> {
			lines.vertex(m, (float) x1, (float) y1, (float) z1).color(c);
			lines.vertex(m, (float) x2, (float) y2, (float) z2).color(c);
		});
		BuiltBuffer built = lines.endNullable();
		if (built != null) BufferRenderer.drawWithGlobalProgram(built);
		RenderSystem.lineWidth(1f);

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		return false;
	}

	private static void box(BufferBuilder b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int c) {
		// bottom, top
		b.vertex(m, x1, y1, z1).color(c); b.vertex(m, x2, y1, z1).color(c); b.vertex(m, x2, y1, z2).color(c); b.vertex(m, x1, y1, z2).color(c);
		b.vertex(m, x1, y2, z1).color(c); b.vertex(m, x1, y2, z2).color(c); b.vertex(m, x2, y2, z2).color(c); b.vertex(m, x2, y2, z1).color(c);
		// north, south
		b.vertex(m, x1, y1, z1).color(c); b.vertex(m, x1, y2, z1).color(c); b.vertex(m, x2, y2, z1).color(c); b.vertex(m, x2, y1, z1).color(c);
		b.vertex(m, x1, y1, z2).color(c); b.vertex(m, x2, y1, z2).color(c); b.vertex(m, x2, y2, z2).color(c); b.vertex(m, x1, y2, z2).color(c);
		// west, east
		b.vertex(m, x1, y1, z1).color(c); b.vertex(m, x1, y1, z2).color(c); b.vertex(m, x1, y2, z2).color(c); b.vertex(m, x1, y2, z1).color(c);
		b.vertex(m, x2, y1, z1).color(c); b.vertex(m, x2, y2, z1).color(c); b.vertex(m, x2, y2, z2).color(c); b.vertex(m, x2, y1, z2).color(c);
	}
}
