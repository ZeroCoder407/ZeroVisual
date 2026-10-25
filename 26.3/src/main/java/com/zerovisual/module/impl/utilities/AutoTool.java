package com.zerovisual.module.impl.utilities;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
	public void render(GuiGraphicsExtractor ctx) {
		if (mc.player == null || mc.level == null || mc.player.isSpectator()) return;
		if (onlyWhileMining.get() && !mc.options.keyAttack.isDown()) return;
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

		BlockState state = mc.level.getBlockState(hit.getBlockPos());
		if (state.isAir() || state.getDestroySpeed(mc.level, hit.getBlockPos()) <= 0) return;

		int selected = mc.player.getInventory().getSelectedSlot();
		int best = -1;
		float bestSpeed = mc.player.getInventory().getItem(selected).getDestroySpeed(state);
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getItem(i);
			float speed = stack.getDestroySpeed(state);
			// a tool that cannot harvest the drop is not "better" even if it is faster
			if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) continue;
			if (speed > bestSpeed + 0.01f) {
				bestSpeed = speed;
				best = i;
			}
		}
		if (best < 0) return;

		float x = ctx.guiWidth() / 2f - 91 + best * 20;
		float y = ctx.guiHeight() - 22;
		float pulse = (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 180.0));
		int accent = Theme.accent();
		Render2D.shadow(ctx, x + 1, y + 1, 20, 20, 4, 4, ColorUtil.withAlpha(accent, 0.5f * pulse));
		Render2D.outline(ctx, x + 1, y + 1, 20, 20, 4, 1.5f, ColorUtil.withAlpha(Theme.accentLight(), 0.6f + 0.4f * pulse));
	}
}
