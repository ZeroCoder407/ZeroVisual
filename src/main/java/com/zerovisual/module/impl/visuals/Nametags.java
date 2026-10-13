package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the vanilla player name tags with styled ones (health, ping, armor).
 * Tags are only drawn where the game itself decided to show a name tag (PlayerEntityRendererMixin),
 * so hidden team name tags stay hidden and sneaking players are not visible through walls.
 */
public class Nametags extends Module {
	private static final EquipmentSlot[] ITEM_SLOTS = {
		EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
	};

	public final NumberSetting scale = add(new NumberSetting("Scale", 1, 0.5, 2, 0.1));
	public final BooleanSetting health = add(new BooleanSetting("Health", true));
	public final BooleanSetting ping = add(new BooleanSetting("Ping", false));
	public final BooleanSetting items = add(new BooleanSetting("Items", true));
	public final BooleanSetting self = add(new BooleanSetting("Self", false));

	private final List<Tag> pending = new ArrayList<>();
	private final ItemRenderState itemState = new ItemRenderState();

	public Nametags() {
		super("Nametags", "Styled player name tags with health, ping and armor", Category.VISUALS);
	}

	/** Called instead of the vanilla label; returns true when the vanilla label must be skipped. */
	public boolean capture(PlayerEntityRenderState state, Text text) {
		if (state.nameLabelPos == null) return false;
		pending.add(new Tag(state.id, new Vec3d(state.x, state.y, state.z).add(state.nameLabelPos), text, state.sneaking));
		return true;
	}

	public void render(WorldRenderContext context) {
		if (mc.player == null || mc.world == null) {
			pending.clear();
			return;
		}
		if (self.get() && mc.options.getPerspective() != Perspective.FIRST_PERSON && !mc.player.isInvisible()) {
			float delta = context.tickCounter().getTickDelta(false);
			Vec3d label = mc.player.getAttachments().getPointNullable(EntityAttachmentType.NAME_TAG, 0, mc.player.getLerpedYaw(delta));
			if (label != null) pending.add(new Tag(mc.player.getId(), mc.player.getLerpedPos(delta).add(label), mc.player.getDisplayName(), mc.player.isSneaking()));
		}
		if (pending.isEmpty()) return;

		Vec3d cam = context.camera().getPos();
		// farthest first so close tags are drawn on top
		pending.sort((a, b) -> Double.compare(b.pos.squaredDistanceTo(cam), a.pos.squaredDistanceTo(cam)));
		Matrix4f view = new Matrix4f(context.positionMatrix());
		VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

		// sneaking players keep the vanilla rule: no tag through walls
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		for (Tag tag : pending) if (tag.sneaking) draw(tag, view, cam, consumers, context);
		RenderSystem.depthMask(true);
		RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT);
		for (Tag tag : pending) if (!tag.sneaking) draw(tag, view, cam, consumers, context);
		pending.clear();
	}

	private void draw(Tag tag, Matrix4f view, Vec3d cam, VertexConsumerProvider.Immediate consumers, WorldRenderContext context) {
		Entity entity = mc.world.getEntityById(tag.id);
		PlayerEntity player = entity instanceof PlayerEntity p ? p : null;
		float s = 0.025f * scale.getFloat();
		Matrix4f m = new Matrix4f(view)
			.translate((float) (tag.pos.x - cam.x), (float) (tag.pos.y + 0.5 - cam.y), (float) (tag.pos.z - cam.z))
			.rotate(context.camera().getRotation())
			.scale(s, -s, s);

		TextRenderer font = mc.textRenderer;
		Text name = tag.text;
		String hp = null;
		int hpColor = 0xFFFFFFFF;
		if (health.get() && player != null) {
			float value = player.getHealth() + player.getAbsorptionAmount();
			hp = value % 1 == 0 ? String.valueOf((int) value) : String.format("%.1f", value);
			float ratio = Math.min(1, player.getHealth() / Math.max(1, player.getMaxHealth()));
			hpColor = ratio > 0.6f ? 0xFF4ADE80 : ratio > 0.3f ? 0xFFFACC15 : 0xFFF43F5E;
			if (player.getAbsorptionAmount() > 0) hpColor = 0xFFFBBF24;
		}
		String latency = null;
		if (ping.get() && player != null && mc.getNetworkHandler() != null) {
			PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
			if (entry != null) latency = entry.getLatency() + "ms";
		}

		Text hpText = hp != null ? Render2D.styled(hp + " ❤") : null;
		Text pingText = latency != null ? Render2D.styled(latency) : null;
		float gap = 5;
		float w = font.getWidth(name);
		if (hpText != null) w += gap + font.getWidth(hpText);
		if (pingText != null) w += gap + font.getWidth(pingText);
		float h = 12;
		float x = -w / 2f, y = -2;

		Render2D.shape(m, x - 5, y - 1, w + 10, h, 4, 0, 0, 0xA00C0C12, 0xA00C0C12, 0xA00C0C12, 0xA00C0C12);
		int accentA = 0xFF388CFF, accentB = 0xFFB050F5;
		Render2D.shape(m, x - 2, y + h - 2.5f, w + 4, 1, 0.5f, 0, 0, ColorUtil.fade(accentA, 0.9f), ColorUtil.fade(accentB, 0.9f),
			ColorUtil.fade(accentB, 0.9f), ColorUtil.fade(accentA, 0.9f));

		float tx = x;
		int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		font.draw(name, tx, y + 1.5f, 0xFFFFFFFF, false, m, consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
		tx += font.getWidth(name) + gap;
		if (hpText != null) {
			font.draw(hpText, tx, y + 1.5f, hpColor, false, m, consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
			tx += font.getWidth(hpText) + gap;
		}
		if (pingText != null) {
			font.draw(pingText, tx, y + 1.5f, 0xFFA1A1B5, false, m, consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
		}
		consumers.draw();

		if (items.get() && player != null) drawItems(player, m, consumers);
	}

	private void drawItems(PlayerEntity player, Matrix4f m, VertexConsumerProvider.Immediate consumers) {
		List<ItemStack> stacks = new ArrayList<>();
		for (EquipmentSlot slot : ITEM_SLOTS) {
			ItemStack stack = player.getEquippedStack(slot);
			if (!stack.isEmpty()) stacks.add(stack);
		}
		if (stacks.isEmpty()) return;

		MatrixStack matrices = new MatrixStack();
		matrices.peek().getPositionMatrix().set(m);
		float startX = -stacks.size() * 16 / 2f;
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			mc.getItemModelManager().update(itemState, stack, ModelTransformationMode.GUI, false, mc.world, player, 0);
			if (itemState.isSideLit()) DiffuseLighting.enableGuiDepthLighting();
			else DiffuseLighting.disableGuiDepthLighting();
			matrices.push();
			matrices.translate(startX + i * 16 + 8, -13, 0);
			matrices.scale(14, -14, 14);
			itemState.render(matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
			matrices.pop();
			consumers.draw();
			if (stack.isItemBarVisible()) {
				float bx = startX + i * 16 + 2, by = -5.5f;
				Render2D.shape(m, bx, by, 12, 1.5f, 0.5f, 0, 0, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000);
				int color = 0xFF000000 | stack.getItemBarColor();
				float step = stack.getItemBarStep();
				Render2D.shape(m, bx, by, 12 * step / 13f, 1.5f, 0.5f, 0, 0, color, color, color, color);
			}
		}
		// restore what WorldRenderer set up for entities
		if (mc.world.getDimensionEffects().isDarkened()) DiffuseLighting.enableForLevel();
		else DiffuseLighting.disableForLevel();
	}

	@Override
	protected void onDisable() {
		pending.clear();
	}

	private record Tag(int id, Vec3d pos, Text text, boolean sneaking) {
	}

}
