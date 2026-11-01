package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.ZvPipelines;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.world.phys.Vec3;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
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
	public boolean render(WorldRenderContext context, BlockOutlineRenderState outline) {
		if (mc.level == null) return true;
		VoxelShape shape = outline.shape();
		if (shape.isEmpty()) return true;
		SubmitNodeCollector collector = context.commandQueue();
		Vec3 cam = context.worldState().cameraRenderState.pos;
		BlockPos pos = outline.pos();
		int c = (useAccent.get() ? Theme.accent() : color.get()) | 0xFF000000;
		float ox = (float) (pos.getX() - cam.x), oy = (float) (pos.getY() - cam.y), oz = (float) (pos.getZ() - cam.z);

		if (fill.get()) {
			int fc = ColorUtil.withAlpha(c, fillOpacity.getFloat());
			Matrix4f m = new Matrix4f().translate(ox, oy, oz);
			float e = 0.002f;
			WorldRender.withCollector(collector, () -> WorldRender.geometry(ZvPipelines.WORLD_COLOR_TYPE, buffer ->
				shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> box(buffer, m,
					(float) x1 - e, (float) y1 - e, (float) z1 - e, (float) x2 + e, (float) y2 + e, (float) z2 + e, fc))));
		}

		PoseStack poseStack = new PoseStack();
		poseStack.translate(ox, oy, oz);
		float width = lineWidth.getFloat();
		collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, lines) -> {
			PoseStack at = new PoseStack();
			at.mulPose(pose.pose());
			net.minecraft.client.renderer.ShapeRenderer.renderShape(at, lines, shape, 0, 0, 0, c, width);
		});
		return false;
	}

	private static void box(VertexConsumer b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int c) {
		// bottom, top
		b.addVertex(m, x1, y1, z1).setColor(c); b.addVertex(m, x2, y1, z1).setColor(c); b.addVertex(m, x2, y1, z2).setColor(c); b.addVertex(m, x1, y1, z2).setColor(c);
		b.addVertex(m, x1, y2, z1).setColor(c); b.addVertex(m, x1, y2, z2).setColor(c); b.addVertex(m, x2, y2, z2).setColor(c); b.addVertex(m, x2, y2, z1).setColor(c);
		// north, south
		b.addVertex(m, x1, y1, z1).setColor(c); b.addVertex(m, x1, y2, z1).setColor(c); b.addVertex(m, x2, y2, z1).setColor(c); b.addVertex(m, x2, y1, z1).setColor(c);
		b.addVertex(m, x1, y1, z2).setColor(c); b.addVertex(m, x2, y1, z2).setColor(c); b.addVertex(m, x2, y2, z2).setColor(c); b.addVertex(m, x1, y2, z2).setColor(c);
		// west, east
		b.addVertex(m, x1, y1, z1).setColor(c); b.addVertex(m, x1, y1, z2).setColor(c); b.addVertex(m, x1, y2, z2).setColor(c); b.addVertex(m, x1, y2, z1).setColor(c);
		b.addVertex(m, x2, y1, z1).setColor(c); b.addVertex(m, x2, y2, z1).setColor(c); b.addVertex(m, x2, y2, z2).setColor(c); b.addVertex(m, x2, y1, z2).setColor(c);
	}
}
