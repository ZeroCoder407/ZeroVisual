package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.render.DrawContext;
import net.minecraft.client.entity.AbstractClientPlayer;
import com.zerovisual.util.TargetTracker;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;

import java.util.Locale;

/** Card with the health, distance and armor of the entity you fight or look at. */
public class TargetHud extends HudModule {
	/** getEquipmentInSlot: helmet 4 down to boots 1, then the held item 0. */
	private static final int[] ARMOR = {4, 3, 2, 1, 0};
	private static final float W = 150;
	private static final float H = 46;

	public final NumberSetting keepTime = add(new NumberSetting("Keep time", 3, 1, 10, 0.5));
	public final BooleanSetting onLook = add(new BooleanSetting("Show on look", true));
	public final BooleanSetting armor = add(new BooleanSetting("Armor", true));

	private final Anim visible = new Anim(12f, 0f);
	private final Anim health = new Anim(8f, 0f);
	private EntityLivingBase shown;

	public TargetHud() {
		super("TargetHUD", "Health, armor and distance of your target");
	}

	private EntityLivingBase currentTarget() {
		if (mc.thePlayer == null) return null;
		EntityLivingBase target = TargetTracker.get(Math.round(keepTime.get() * 1000));
		if (target != null) return target;
		MovingObjectPosition hit = mc.objectMouseOver;
		if (onLook.get() && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit instanceof EntityLivingBase) {
			return (EntityLivingBase) hit.entityHit;
		}
		// preview on yourself while positioning in the chat
		if (isEditing()) return mc.thePlayer;
		return null;
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		return new float[]{screenW / 2f - w / 2, screenH / 2f + 26};
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		EntityLivingBase current = currentTarget();
		if (current != null && current != shown) {
			if (shown == null) health.set(current.getHealth() / current.getMaxHealth());
			shown = current;
		}
		float v = visible.update(current != null);
		if (v <= 0.01f || shown == null) {
			if (v <= 0.01f) shown = null;
			return;
		}

		float[] pos = place(ctx, W, H);
		float x = pos[0];
		float y = pos[1];

		float prevAlpha = Render2D.getAlpha();
		Render2D.setAlpha(v);
		ctx.getMatrices().push();
		float scale = 0.9f + 0.1f * v;
		ctx.getMatrices().translate(x + W / 2, y + H / 2, 0);
		ctx.getMatrices().scale(scale, scale, 1);
		ctx.getMatrices().translate(-(x + W / 2), -(y + H / 2), 0);

		Theme.card(ctx, x, y, W, H);
		drawHead(ctx, shown, x + 6, y + 6, 34);

		float tx = x + 46;
		String name = shown.getName();
		Render2D.text(ctx, name, tx, y + 6, Theme.TEXT);
		if (mc.thePlayer != null) {
			String dist = String.format(Locale.ROOT, "%.1fm", mc.thePlayer.getDistanceToEntity(shown));
			Render2D.text(ctx, dist, x + W - 7 - Render2D.textWidth(dist), y + 6, Theme.TEXT_DIM);
		}

		float hp = shown.getHealth();
		float max = Math.max(1, shown.getMaxHealth());
		float absorption = shown.getAbsorptionAmount();
		float shownHp = health.update(Math.min(1, hp / max));
		String hpText = String.format(Locale.ROOT, "%.1f", hp) + (absorption > 0 ? String.format(Locale.ROOT, " +%.0f", absorption) : "") + " HP";
		Render2D.text(ctx, hpText, tx, y + 17, ColorUtil.lerp(0xFFFF5C6C, Theme.TEXT_MUTED, hp / max));

		float barX = tx, barY = y + 29, barW = W - 46 - 7, barH = 4;
		Render2D.rect(ctx, barX, barY, barW, barH, 2, Theme.TRACK);
		Render2D.gradientH(ctx, barX, barY, Math.max(barH, barW * shownHp), barH, 2, Theme.accent(), Theme.accentLight());
		if (absorption > 0) {
			Render2D.rect(ctx, barX, barY, Math.max(barH, barW * Math.min(1, absorption / max)), barH, 2, 0xC0FFC83C);
		}

		if (armor.get()) {
			ctx.getMatrices().push();
			ctx.getMatrices().translate(tx, y + 35, 0);
			ctx.getMatrices().scale(0.5f, 0.5f, 1);
			int ix = 0;
			for (int slot : ARMOR) {
				ItemStack stack = shown.getEquipmentInSlot(slot);
				if (stack != null) ctx.drawItem(stack, ix, 0);
				ix += 18;
			}
			ctx.getMatrices().pop();
		}

		ctx.getMatrices().pop();
		Render2D.setAlpha(prevAlpha);
	}

	private void drawHead(DrawContext ctx, EntityLivingBase entity, float x, float y, float size) {
		Render2D.rect(ctx, x, y, size, size, 6, 0xFF14141B);
		if (entity instanceof AbstractClientPlayer) {
			AbstractClientPlayer player = (AbstractClientPlayer) entity;
			net.minecraft.client.renderer.GlStateManager.color(1, 1, 1, Render2D.getAlpha());
			com.zerovisual.util.Skins.drawHead(player.getLocationSkin(), (int) x + 3, (int) y + 3, (int) size - 6);
			// red flash while hurt
			if (player.hurtTime > 0) Render2D.rect(ctx, x + 3, y + 3, size - 6, size - 6, 2, ColorUtil.withAlpha(0xFF3040, player.hurtTime / 10f * 0.5f));
		} else {
			Render2D.gradientV(ctx, x + 3, y + 3, size - 6, size - 6, 4, Theme.accentLight(), Theme.accent());
			String letter = entity.getName().substring(0, 1).toUpperCase();
			Render2D.scaledText(ctx, letter, x + size / 2 - Render2D.textWidth(letter), y + size / 2 - 8, 2f, 0xFFFFFFFF);
		}
	}
}
