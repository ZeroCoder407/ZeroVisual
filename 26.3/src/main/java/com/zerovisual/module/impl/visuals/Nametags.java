package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.render.WorldContext;
import com.zerovisual.render.WorldRender;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the vanilla player name tags with styled ones (health, ping, armor).
 * Tags are only drawn where the game itself decided to show a name tag (AvatarRendererMixin replaces the
 * vanilla tag in place), so hidden team name tags stay hidden and sneaking players are not visible through walls.
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

	public Nametags() {
		super("Nametags", "Styled player name tags with health, ping and armor", Category.VISUALS);
	}

	/**
	 * Called instead of the vanilla name tag of a player, while the player is submitted.
	 * Returns true when the vanilla tag must be skipped.
	 */
	public boolean replace(AvatarRenderState state, SubmitNodeCollector collector) {
		if (state.nameTag == null || state.nameTagAttachment == null) return false;
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 pos = new Vec3(state.x, state.y, state.z).add(state.nameTagAttachment);
		WorldRender.withCollector(collector, () -> draw(state.id, pos, state.nameTag, state.isDiscrete, camera));
		return true;
	}

	/** Your own tag in third person (the game never shows it). */
	public void render(WorldContext context) {
		if (mc.player == null || mc.level == null || !self.get()) return;
		if (mc.options.getCameraType() == CameraType.FIRST_PERSON || mc.player.isInvisible()) return;
		float delta = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 label = mc.player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, mc.player.getYRot(delta));
		if (label == null) return;
		draw(mc.player.getId(), mc.player.getPosition(delta).add(label), mc.player.getDisplayName(), mc.player.isShiftKeyDown(), context.camera());
	}

	private void draw(int id, Vec3 pos, Component name, boolean sneaking, Camera camera) {
		Entity entity = mc.level != null ? mc.level.getEntity(id) : null;
		Player player = entity instanceof Player p ? p : null;
		// sneaking players keep the vanilla rule: no tag through walls
		boolean seeThrough = !sneaking;
		Vec3 cam = camera.position();
		float s = 0.025f * scale.getFloat();
		Matrix4f m = new Matrix4f()
			.translate((float) (pos.x - cam.x), (float) (pos.y + 0.5 - cam.y), (float) (pos.z - cam.z))
			.rotate(camera.rotation())
			.scale(s, -s, s);

		Font font = mc.font;
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
		if (ping.get() && player != null && mc.getConnection() != null) {
			PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
			if (entry != null) latency = entry.getLatency() + "ms";
		}

		Component hpText = hp != null ? Render2D.styled(hp + " ❤") : null;
		Component pingText = latency != null ? Render2D.styled(latency) : null;
		float gap = 5;
		float w = font.width(name);
		if (hpText != null) w += gap + font.width(hpText);
		if (pingText != null) w += gap + font.width(pingText);
		float h = 12;
		float x = -w / 2f, y = -2;

		int bg = 0xA00C0C12;
		WorldRender.shape(m, x - 5, y - 1, w + 10, h, 4, 0, 0, bg, bg, bg, bg, seeThrough);
		int accentA = ColorUtil.fade(0xFF388CFF, 0.9f), accentB = ColorUtil.fade(0xFFB050F5, 0.9f);
		WorldRender.shape(m, x - 2, y + h - 2.5f, w + 4, 1, 0.5f, 0, 0, accentA, accentB, accentB, accentA, seeThrough);

		float tx = x;
		WorldRender.text(m, name, tx, y + 1.5f, 0xFFFFFFFF, 0, seeThrough, false);
		tx += font.width(name) + gap;
		if (hpText != null) {
			WorldRender.text(m, hpText, tx, y + 1.5f, hpColor, 0, seeThrough, false);
			tx += font.width(hpText) + gap;
		}
		if (pingText != null) WorldRender.text(m, pingText, tx, y + 1.5f, 0xFFA1A1B5, 0, seeThrough, false);

		if (items.get() && player != null) drawItems(player, m, seeThrough);
	}

	private void drawItems(Player player, Matrix4f m, boolean seeThrough) {
		SubmitNodeCollector collector = WorldRender.collector();
		if (collector == null) return;
		List<ItemStack> stacks = new ArrayList<>();
		for (EquipmentSlot slot : ITEM_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty()) stacks.add(stack);
		}
		if (stacks.isEmpty()) return;

		float startX = -stacks.size() * 16 / 2f;
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			// a state per item: submissions are rendered later
			ItemStackRenderState itemState = new ItemStackRenderState();
			mc.getItemModelResolver().updateForTopItem(itemState, stack, ItemDisplayContext.GUI, mc.level, player, 0);
			PoseStack matrices = new PoseStack();
			matrices.last().pose().set(m);
			matrices.translate(startX + i * 16 + 8, -13, 0);
			matrices.scale(14, -14, 14);
			itemState.submit(matrices, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			if (stack.isBarVisible()) {
				float bx = startX + i * 16 + 2, by = -5.5f;
				WorldRender.shape(m, bx, by, 12, 1.5f, 0.5f, 0, 0, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, seeThrough);
				int color = 0xFF000000 | stack.getBarColor();
				float step = stack.getBarWidth();
				WorldRender.shape(m, bx, by, 12 * step / 13f, 1.5f, 0.5f, 0, 0, color, color, color, color, seeThrough);
			}
		}
	}
}
