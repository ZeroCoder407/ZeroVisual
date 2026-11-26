package com.zerovisual.module.impl.visuals;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;

/** Colored outline and translucent fill of the targeted block, replacing the vanilla black lines. */
public class BlockOutline extends Module {
	public final BooleanSetting useAccent = add(new BooleanSetting("Accent color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF8B5CF6)
		.visibleWhen(() -> !useAccent.get()));
	public final NumberSetting lineWidth = add(new NumberSetting("Line width", 2, 1, 5, 0.5));
	public final BooleanSetting fill = add(new BooleanSetting("Fill", true));
	public final NumberSetting fillOpacity = add(new NumberSetting("Fill opacity", 0.15, 0.05, 0.6, 0.05)
		.visibleWhen(() -> fill.get()));

	public BlockOutline() {
		super("BlockOutline", "Colored outline and fill of the targeted block", Category.VISUALS);
	}

	/** From DrawBlockHighlightEvent; @return false to keep the vanilla outline */
	public boolean render(MovingObjectPosition target, Entity viewer, float partialTicks) {
		if (mc.theWorld == null || target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return false;
		BlockPos pos = target.getBlockPos();
		Block block = mc.theWorld.getBlockState(pos).getBlock();
		if (block.getMaterial() == Material.air || !mc.theWorld.getWorldBorder().contains(pos)) return false;
		block.setBlockBoundsBasedOnState(mc.theWorld, pos);
		double cx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
		double cy = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
		double cz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;
		AxisAlignedBB box = block.getSelectedBoundingBox(mc.theWorld, pos).expand(0.002, 0.002, 0.002).offset(-cx, -cy, -cz);

		int c = (useAccent.get() ? Theme.accent() : color.get()) | 0xFF000000;
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		GlStateManager.disableTexture2D();
		GlStateManager.depthMask(false);
		GlStateManager.disableCull();
		if (fill.get()) box(box, ColorUtil.withAlpha(c, fillOpacity.getFloat()));
		GL11.glLineWidth(lineWidth.getFloat());
		edges(box, c);
		GL11.glLineWidth(1f);
		GlStateManager.enableCull();
		GlStateManager.depthMask(true);
		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
		return true;
	}

	private static void box(AxisAlignedBB b, int c) {
		WorldRenderer w = Tessellator.getInstance().getWorldRenderer();
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
		double[][] faces = {
			{x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2}, {x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1},
			{x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1}, {x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2},
			{x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1}, {x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2}};
		for (double[] f : faces) {
			for (int i = 0; i < 12; i += 3) vertex(w, f[i], f[i + 1], f[i + 2], c);
		}
		Tessellator.getInstance().draw();
	}

	private static void edges(AxisAlignedBB b, int c) {
		WorldRenderer w = Tessellator.getInstance().getWorldRenderer();
		w.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
		double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
		for (double y : ys) {
			for (double z : zs) {
				vertex(w, xs[0], y, z, c);
				vertex(w, xs[1], y, z, c);
			}
			for (double x : xs) {
				vertex(w, x, y, zs[0], c);
				vertex(w, x, y, zs[1], c);
			}
		}
		for (double x : xs) {
			for (double z : zs) {
				vertex(w, x, ys[0], z, c);
				vertex(w, x, ys[1], z, c);
			}
		}
		Tessellator.getInstance().draw();
	}

	private static void vertex(WorldRenderer w, double x, double y, double z, int c) {
		w.pos(x, y, z).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
	}
}
