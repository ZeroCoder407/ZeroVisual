package com.zerovisual.module.impl.visuals;

import net.minecraft.client.renderer.GlStateManager;
import com.zerovisual.render.Mesh;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.WorldRenderContext;
import net.minecraft.entity.Entity;
import com.zerovisual.util.Vec3d;
import org.joml.Matrix4f;

/**
 * Colors the hitbox of the entity under the crosshair. With F3+B the vanilla box of that entity is recolored
 * (EntityRenderDispatcherMixin); without it the box can be drawn anyway. The crosshair target is only ever an
 * entity you can see and reach, and the box is hidden behind blocks like any other line.
 */
public class HitboxColor extends Module implements com.zerovisual.ZeroVisualClient.WorldEffect {
	public final BooleanSetting useAccent = add(new BooleanSetting("Accent color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFF43F5E)
		.visibleWhen(() -> !useAccent.get()));
	public final BooleanSetting withoutF3 = add(new BooleanSetting("Without F3+B", true));
	public final NumberSetting lineWidth = add(new NumberSetting("Line width", 2, 1, 5, 0.5)
		.visibleWhen(() -> withoutF3.get()));

	public HitboxColor() {
		super("HitboxColor", "Colored hitbox of the player or mob under the crosshair", Category.VISUALS);
	}

	public int boxColor() {
		return (useAccent.get() ? Theme.accent() : color.get()) | 0xFF000000;
	}

	/** EntityRenderDispatcherMixin: the vanilla hitbox of this entity gets {@link #boxColor()}. */
	public boolean recolors(Entity entity) {
		return isEnabled() && entity == mc.pointedEntity;
	}

	/** Own box when the vanilla hitboxes are off. */
	@Override
	public void render(WorldRenderContext context) {
		if (!withoutF3.get() || mc.getRenderManager().isDebugBoundingBox()) return;
		Entity target = mc.pointedEntity;
		if (target == null || target.isInvisible()) return;
		float tickDelta = context.tickCounter().getTickDelta(true);
		Vec3d cam = context.camera().getPos();
		Vec3d offset = Vec3d.lerpedPos(target, tickDelta).subtract(Vec3d.pos(target)).subtract(cam);
		net.minecraft.util.AxisAlignedBB box = target.getEntityBoundingBox().offset(offset.x, offset.y, offset.z);
		int c = boxColor();
		Matrix4f m = new Matrix4f();

		GlStateManager.enableBlend();
		Mesh.defaultBlendFunc();
		GlStateManager.enableDepth();
		GlStateManager.depthMask(false);
		org.lwjgl.opengl.GL11.glLineWidth(lineWidth.getFloat());
		Mesh lines = Mesh.begin(Mesh.DEBUG_LINES);
		float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ, x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
		float[][] edges = {
			{x1, y1, z1, x2, y1, z1}, {x2, y1, z1, x2, y1, z2}, {x2, y1, z2, x1, y1, z2}, {x1, y1, z2, x1, y1, z1},
			{x1, y2, z1, x2, y2, z1}, {x2, y2, z1, x2, y2, z2}, {x2, y2, z2, x1, y2, z2}, {x1, y2, z2, x1, y2, z1},
			{x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x2, y1, z2, x2, y2, z2}, {x1, y1, z2, x1, y2, z2}
		};
		for (float[] e : edges) {
			lines.vertex(m, e[0], e[1], e[2]).color(c);
			lines.vertex(m, e[3], e[4], e[5]).color(c);
		}
		lines.draw();
		org.lwjgl.opengl.GL11.glLineWidth(1f);
		GlStateManager.depthMask(true);
		GlStateManager.disableBlend();
	}
}
