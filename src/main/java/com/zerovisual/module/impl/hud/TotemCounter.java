package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Number of Totems of Undying in the inventory (including the offhand). */
public class TotemCounter extends HudModule {
	private static final ItemStack ICON = new ItemStack(Items.TOTEM_OF_UNDYING);

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
	public void render(DrawContext ctx, float tickDelta) {
		if (mc.player == null) return;
		int count = 0;
		PlayerInventory inventory = mc.player.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isOf(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
		}
		if (count == 0 && !showZero.get() && !isEditing()) return;

		String text = String.valueOf(count);
		float w = 6 + 16 + 4 + Render2D.textWidth(text) + 8;
		float h = 22;
		float k = scale.getFloat();
		float[] pos = place(ctx, w * k, h * k);

		ctx.getMatrices().push();
		ctx.getMatrices().translate(pos[0], pos[1], 0);
		ctx.getMatrices().scale(k, k, 1);
		Theme.card(ctx, 0, 0, w, h, background.getFloat());
		ctx.draw();
		ctx.drawItem(ICON, 5, 3);
		int color = count == 0 ? 0xFFFF5C6C : count == 1 ? 0xFFFFB454 : 0xFFFFD866;
		Render2D.text(ctx, text, 6 + 16 + 4, 7, color);
		ctx.getMatrices().pop();
	}
}
