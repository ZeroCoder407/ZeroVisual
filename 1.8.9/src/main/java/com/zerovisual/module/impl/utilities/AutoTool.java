package com.zerovisual.module.impl.utilities;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.DrawContext;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;

/**
 * Highlights the hotbar slot with the best tool for the block you are looking at.
 * A hint only: it never switches the slot for you.
 */
public class AutoTool extends Module {
	public final BooleanSetting onlyWhileMining = add(new BooleanSetting("Only while mining", true));

	public AutoTool() {
		super("AutoTool", "Highlights the best hotbar tool for the block", Category.UTILITIES);
	}

	/** Drawn on the HUD after the vanilla hotbar. */
	public void render(DrawContext ctx) {
		if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isSpectator()) return;
		if (onlyWhileMining.get() && !mc.gameSettings.keyBindAttack.isKeyDown()) return;
		MovingObjectPosition hit = mc.objectMouseOver;
		if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
		BlockPos pos = hit.getBlockPos();
		Block block = mc.theWorld.getBlockState(pos).getBlock();
		if (block.getMaterial() == Material.air || block.getBlockHardness(mc.theWorld, pos) <= 0) return;

		int selected = mc.thePlayer.inventory.currentItem;
		int best = -1;
		float bestSpeed = speed(mc.thePlayer.inventory.getStackInSlot(selected), block);
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
			// a tool that cannot harvest the drop is not "better" even if it is faster
			if (!block.getMaterial().isToolNotRequired() && (stack == null || !stack.canHarvestBlock(block))) continue;
			float speed = speed(stack, block);
			if (speed > bestSpeed + 0.01f) {
				bestSpeed = speed;
				best = i;
			}
		}
		if (best < 0) return;

		float x = ctx.getScaledWindowWidth() / 2f - 91 + best * 20;
		float y = ctx.getScaledWindowHeight() - 22;
		float pulse = (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 180.0));
		int accent = Theme.accent();
		Render2D.shadow(ctx, x + 1, y + 1, 20, 20, 4, 4, ColorUtil.withAlpha(accent, 0.5f * pulse));
		Render2D.outline(ctx, x + 1, y + 1, 20, 20, 4, 1.5f, ColorUtil.withAlpha(Theme.accentLight(), 0.6f + 0.4f * pulse));
	}

	private static float speed(ItemStack stack, Block block) {
		return stack == null ? 1 : stack.getStrVsBlock(block);
	}
}
