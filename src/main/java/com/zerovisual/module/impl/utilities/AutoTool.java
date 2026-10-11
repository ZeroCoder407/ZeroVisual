package com.zerovisual.module.impl.utilities;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Highlights the hotbar slot with the best tool for the block you are looking at.
 * A hint only: it never switches the slot for you.
 */
public class AutoTool extends Module {
	public final BooleanSetting onlyWhileMining = add(new BooleanSetting("Only while mining", true));

	public AutoTool() {
		super("AutoTool", "Подсвечивает лучший инструмент для блока в хотбаре", Category.UTILITIES);
	}

	/** Drawn on the HUD after the vanilla hotbar. */
	public void render(DrawContext ctx) {
		if (mc.player == null || mc.world == null || mc.player.isSpectator()) return;
		if (onlyWhileMining.get() && !mc.options.attackKey.isPressed()) return;
		if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

		BlockState state = mc.world.getBlockState(hit.getBlockPos());
		if (state.isAir() || state.getHardness(mc.world, hit.getBlockPos()) <= 0) return;

		int selected = mc.player.getInventory().selectedSlot;
		int best = -1;
		float bestSpeed = mc.player.getInventory().getStack(selected).getMiningSpeedMultiplier(state);
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);
			float speed = stack.getMiningSpeedMultiplier(state);
			// a tool that cannot harvest the drop is not "better" even if it is faster
			if (state.isToolRequired() && !stack.isSuitableFor(state)) continue;
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
}
