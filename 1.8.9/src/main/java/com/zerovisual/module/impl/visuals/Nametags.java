package com.zerovisual.module.impl.visuals;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.WorldRenderContext;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.util.Vec3d;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Styled player name tags with health, ping and the held item and armor. Only where the game would show a tag:
 * the vanilla tag is caught (RenderLivingEvent.Specials) and drawn here after the world instead, so no ESP.
 */
public class Nametags extends Module implements ZeroVisualClient.WorldEffect {
	public final NumberSetting scale = add(new NumberSetting("Scale", 1, 0.5, 2, 0.1));
	public final BooleanSetting health = add(new BooleanSetting("Health", true));
	public final BooleanSetting ping = add(new BooleanSetting("Ping", false));
	public final BooleanSetting items = add(new BooleanSetting("Items", true));
	public final BooleanSetting self = add(new BooleanSetting("Self", false));

	private final List<Tag> pending = new ArrayList<Tag>();

	public Nametags() {
		super("Nametags", "Styled player name tags with health, ping and armor", Category.VISUALS);
	}

	/** Vanilla only gets here when the name tag should be visible; the tag is drawn later in its own style. */
	@SubscribeEvent
	public void onVanillaTag(RenderLivingEvent.Specials.Pre<?> event) {
		if (!isEnabled() || !(event.entity instanceof EntityPlayer)) return;
		// Mini me of Pet has no tag at all
		if (event.entity == com.zerovisual.module.impl.cosmetics.Pet.posedState) return;
		Entity e = event.entity;
		// event.x/y/z are relative to the render position of the camera
		Vec3d pos = new Vec3d(event.x, event.y, event.z).add(mc.getRenderManager().viewerPosX, mc.getRenderManager().viewerPosY, mc.getRenderManager().viewerPosZ);
		pending.add(new Tag(e.getEntityId(), pos.add(0, e.height + 0.5, 0), e.getDisplayName().getFormattedText(), e.isSneaking()));
		event.setCanceled(true);
	}

	@Override
	public void render(WorldRenderContext context) {
		if (mc.thePlayer == null || mc.theWorld == null) {
			pending.clear();
			return;
		}
		if (self.get() && mc.gameSettings.thirdPersonView != 0 && !mc.thePlayer.isInvisible()) {
			float delta = context.tickCounter().getTickDelta(false);
			pending.add(new Tag(mc.thePlayer.getEntityId(), Vec3d.lerpedPos(mc.thePlayer, delta).add(0, mc.thePlayer.height + 0.5, 0),
				mc.thePlayer.getDisplayName().getFormattedText(), mc.thePlayer.isSneaking()));
		}
		if (pending.isEmpty()) return;

		final Vec3d cam = context.camera().getPos();
		// farthest first so close tags are drawn on top
		pending.sort((a, b) -> Double.compare(b.pos.squaredDistanceTo(cam), a.pos.squaredDistanceTo(cam)));

		WorldRender.begin();
		// sneaking players keep the vanilla rule: no tag through walls
		for (Tag tag : pending) if (tag.sneaking) draw(tag, cam, context);
		GlStateManager.disableDepth();
		for (Tag tag : pending) if (!tag.sneaking) draw(tag, cam, context);
		GlStateManager.enableDepth();
		WorldRender.end();
		pending.clear();
	}

	private void draw(Tag tag, Vec3d cam, WorldRenderContext context) {
		Entity entity = mc.theWorld.getEntityByID(tag.id);
		EntityPlayer player = entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
		float s = 0.025f * scale.getFloat();
		Matrix4f m = WorldRender.billboard(tag.pos, cam, context.camera().getRotation()).scale(s, -s, s);

		FontRenderer font = mc.fontRendererObj;
		String name = tag.text;
		String hp = null;
		int hpColor = 0xFFFFFFFF;
		if (health.get() && player != null) {
			float value = player.getHealth() + player.getAbsorptionAmount();
			hp = value % 1 == 0 ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.1f", value);
			float ratio = Math.min(1, player.getHealth() / Math.max(1, player.getMaxHealth()));
			hpColor = ratio > 0.6f ? 0xFF4ADE80 : ratio > 0.3f ? 0xFFFACC15 : 0xFFF43F5E;
			if (player.getAbsorptionAmount() > 0) hpColor = 0xFFFBBF24;
		}
		String latency = null;
		if (ping.get() && player != null && mc.getNetHandler() != null) {
			NetworkPlayerInfo entry = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
			if (entry != null) latency = entry.getResponseTime() + "ms";
		}

		String hpText = hp != null ? hp + " ❤" : null;
		float gap = 5;
		float w = font.getStringWidth(name);
		if (hpText != null) w += gap + font.getStringWidth(hpText);
		if (latency != null) w += gap + font.getStringWidth(latency);
		float h = 12;
		float x = -w / 2f, y = -2;

		WorldRender.shape(m, x - 5, y - 1, w + 10, h, 4, 0, 0, 0xA00C0C12, 0xA00C0C12, 0xA00C0C12, 0xA00C0C12);
		int accentA = 0xFF388CFF, accentB = 0xFFB050F5;
		WorldRender.shape(m, x - 2, y + h - 2.5f, w + 4, 1, 0.5f, 0, 0, ColorUtil.fade(accentA, 0.9f), ColorUtil.fade(accentB, 0.9f),
			ColorUtil.fade(accentB, 0.9f), ColorUtil.fade(accentA, 0.9f));

		float tx = x;
		WorldRender.text(m, name, tx, y + 1.5f, 0xFFFFFFFF, 0, false, false);
		tx += font.getStringWidth(name) + gap;
		if (hpText != null) {
			WorldRender.text(m, hpText, tx, y + 1.5f, hpColor, 0, false, false);
			tx += font.getStringWidth(hpText) + gap;
		}
		if (latency != null) WorldRender.text(m, latency, tx, y + 1.5f, 0xFFA1A1B5, 0, false, false);

		if (items.get() && player != null) drawItems(player, m);
	}

	/** The held item and the armor above the tag, as small 3D items. */
	private void drawItems(EntityPlayer player, Matrix4f m) {
		List<ItemStack> stacks = new ArrayList<ItemStack>();
		if (player.getHeldItem() != null) stacks.add(player.getHeldItem());
		for (int slot = 3; slot >= 0; slot--) {
			ItemStack stack = player.inventory.armorInventory[slot];
			if (stack != null) stacks.add(stack);
		}
		if (stacks.isEmpty()) return;

		float startX = -stacks.size() * 16 / 2f;
		GlStateManager.enableTexture2D();
		GlStateManager.enableAlpha();
		GlStateManager.enableRescaleNormal();
		RenderHelper.enableGUIStandardItemLighting();
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			WorldRender.push(m);
			GlStateManager.translate(startX + i * 16 + 8, -13, 0);
			GlStateManager.scale(14, -14, 14);
			mc.getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.GUI);
			GlStateManager.popMatrix();
		}
		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableRescaleNormal();
		GlStateManager.disableLighting();
		GlStateManager.disableAlpha();
		GlStateManager.enableBlend();
		GL11.glDisable(GL11.GL_LIGHTING);

		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			if (!stack.isItemDamaged()) continue;
			float left = 1f - stack.getItemDamage() / (float) stack.getMaxDamage();
			float bx = startX + i * 16 + 2, by = -5.5f;
			WorldRender.shape(m, bx, by, 12, 1.5f, 0.5f, 0, 0, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000);
			int color = 0xFF000000 | java.awt.Color.HSBtoRGB(left / 3f, 1f, 1f);
			WorldRender.shape(m, bx, by, 12 * left, 1.5f, 0.5f, 0, 0, color, color, color, color);
		}
	}

	@Override
	protected void onDisable() {
		pending.clear();
	}

	private static final class Tag {
		final int id;
		final Vec3d pos;
		final String text;
		final boolean sneaking;

		Tag(int id, Vec3d pos, String text, boolean sneaking) {
			this.id = id;
			this.pos = pos;
			this.text = text;
			this.sneaking = sneaking;
		}
	}
}
