package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Number of Totems of Undying in the inventory (including the offhand). */
public class TotemCounter extends HudModule {
	// created on first draw: item components are bound only after the registries load
	private static ItemStack icon;

	public final BooleanSetting showZero = add(new BooleanSetting("Show when zero", true));
	public final NumberSetting background = add(new NumberSetting("Background", 0.88, 0, 1, 0.05));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1, 0.5, 2, 0.1));

	public TotemCounter() {
		super("TotemCounter", "How many totems of undying are in your inventory");
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		// left of the hotbar
		return new float[]{screenW / 2f - 91 - 6 - w - 24, screenH - h - 2};
	}

	@Override
	public void render(GuiGraphics ctx, float tickDelta) {
		if (mc.player == null) return;
		int count = 0;
		Inventory inventory = mc.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
		}
		if (count == 0 && !showZero.get() && !isEditing()) return;

		String text = String.valueOf(count);
		float w = 6 + 16 + 4 + Render2D.textWidth(text) + 8;
		float h = 22;
		float k = scale.getFloat();
		float[] pos = place(ctx, w * k, h * k);

		ctx.pose().pushMatrix();
		ctx.pose().translate(pos[0], pos[1]);
		ctx.pose().scale(k, k);
		Theme.card(ctx, 0, 0, w, h, background.getFloat());
		if (icon == null) icon = new ItemStack(Items.TOTEM_OF_UNDYING);
		ctx.renderItem(icon, 5, 3);
		int color = count == 0 ? 0xFFFF5C6C : count == 1 ? 0xFFFFB454 : 0xFFFFD866;
		Render2D.text(ctx, text, 6 + 16 + 4, 7, color);
		ctx.pose().popMatrix();
	}
}
