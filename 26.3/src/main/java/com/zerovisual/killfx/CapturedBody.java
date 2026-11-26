package com.zerovisual.killfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * A snapshot of a dying entity's render state, so an effect can keep drawing the body itself
 * (the real entity is hidden by KillEffect while the effect runs). Same idea as KillEffect's black hole.
 */
public final class CapturedBody {
	public final EntityRenderState state;
	@SuppressWarnings("rawtypes")
	public final EntityRenderer renderer;
	public final int entityId;
	public final Vec3 pos;
	public final float height;
	public final int light;

	@SuppressWarnings("rawtypes")
	private CapturedBody(EntityRenderState state, EntityRenderer renderer, int entityId, Vec3 pos, float height, int light) {
		this.state = state;
		this.renderer = renderer;
		this.entityId = entityId;
		this.pos = pos;
		this.height = height;
		this.light = light;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static CapturedBody of(LivingEntity entity) {
		Minecraft mc = Minecraft.getInstance();
		EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
		EntityRenderState state = renderer.createRenderState();
		renderer.extractRenderState(entity, state, 1f);
		state.nameTag = null;
		state.displayFireAnimation = false;
		if (state instanceof LivingEntityRenderState living) {
			living.deathTime = 0;
			living.hasRedOverlay = false;
			living.walkAnimationSpeed = 0;
		}
		int light = mc.getEntityRenderDispatcher().getPackedLightCoords(entity, 1f);
		return new CapturedBody(state, renderer, entity.getId(), entity.position(), entity.getBbHeight(), light);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void submit(PoseStack matrices, SubmitNodeCollector collector, CameraRenderState cameraState) {
		Vec3 offset = renderer.getRenderOffset(state);
		matrices.translate(offset.x, offset.y, offset.z);
		if (state instanceof EntityRenderState es) es.lightCoords = light;
		renderer.submit(state, matrices, collector, cameraState);
	}
}
