package com.zerovisual.killfx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * A snapshot of a dying entity's render state, so an effect can keep drawing the body itself
 * (the real entity is hidden by KillEffect while the effect runs).
 */
public final class CapturedBody {
	public final EntityRenderState state;
	@SuppressWarnings("rawtypes")
	public final EntityRenderer renderer;
	public final int entityId;
	public final Vec3d pos;
	public final float height;
	public final int light;

	@SuppressWarnings("rawtypes")
	private CapturedBody(EntityRenderState state, EntityRenderer renderer, int entityId, Vec3d pos, float height, int light) {
		this.state = state;
		this.renderer = renderer;
		this.entityId = entityId;
		this.pos = pos;
		this.height = height;
		this.light = light;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static CapturedBody of(LivingEntity entity) {
		MinecraftClient mc = MinecraftClient.getInstance();
		EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
		EntityRenderState state = renderer.createRenderState();
		renderer.updateRenderState(entity, state, 1f);
		state.displayName = null;
		state.onFire = false;
		if (state instanceof LivingEntityRenderState living) {
			living.deathTime = 0;
			living.hurt = false;
			living.limbAmplitudeMultiplier = 0;
		}
		int light = mc.getEntityRenderDispatcher().getLight(entity, 1f);
		return new CapturedBody(state, renderer, entity.getId(), entity.getPos(), entity.getHeight(), light);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void submit(MatrixStack matrices, VertexConsumerProvider.Immediate consumers) {
		Vec3d offset = renderer.getPositionOffset(state);
		matrices.translate(offset.x, offset.y, offset.z);
		renderer.render(state, matrices, consumers, light);
		consumers.draw();
	}
}
