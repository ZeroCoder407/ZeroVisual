package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.WorldContext;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ColorSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Colors the hitbox of the entity under the crosshair. With F3+B the vanilla box of that entity is recolored
 * (EntityHitboxDebugRendererMixin); without it the box can be drawn anyway. The crosshair target is only ever an
 * entity you can see and reach, and the box is hidden behind blocks like any other line.
 */
public class HitboxColor extends Module {
	public final BooleanSetting useAccent = add(new BooleanSetting("Accent color", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFF43F5E)
		.visibleWhen(() -> !useAccent.get()));
	public final BooleanSetting withoutF3 = add(new BooleanSetting("Without F3+B", true));
	public final NumberSetting lineWidth = add(new NumberSetting("Line width", 2, 1, 5, 0.5)
		.visibleWhen(withoutF3::get));

	public HitboxColor() {
		super("HitboxColor", "Colored hitbox of the player or mob under the crosshair", Category.VISUALS);
	}

	public int boxColor() {
		return (useAccent.get() ? Theme.accent() : color.get()) | 0xFF000000;
	}

	/** EntityHitboxDebugRendererMixin: the vanilla hitbox of this entity gets {@link #boxColor()}. */
	public boolean recolors(Entity entity) {
		return isEnabled() && entity == mc.crosshairPickEntity;
	}

	/** Own box when the vanilla hitboxes are off. */
	public void render(WorldContext context) {
		if (!withoutF3.get() || mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) return;
		Entity target = mc.crosshairPickEntity;
		SubmitNodeCollector collector = WorldRender.collector();
		if (target == null || target.isInvisible() || collector == null) return;
		float delta = context.tickCounter().getGameTimeDeltaPartialTick(true);
		Vec3 cam = context.cameraState().pos;
		Vec3 pos = target.getPosition(delta);
		AABB box = target.getBoundingBox().move(-target.getX(), -target.getY(), -target.getZ());
		PoseStack poseStack = new PoseStack();
		poseStack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
		float width = lineWidth.getFloat();
		collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, lines) -> {
			PoseStack at = new PoseStack();
			at.mulPose(pose.pose());
			net.minecraft.client.renderer.ShapeRenderer.renderShape(at, lines, Shapes.create(box), 0, 0, 0, boxColor(), width);
		});
	}
}
