package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Items glide from slot to slot in inventories (shift click, number keys, double click) instead of jumping.
 * Each frame the slots are compared with the previous frame; a stack that left one slot and showed up in another
 * flies between them, and the target slot is hidden until it lands (AbstractContainerScreenMixin).
 */
public class SmoothSwap extends Module {
	public final NumberSetting duration = add(new NumberSetting("Duration ms", 160, 60, 400, 10));

	private record Change(int slot, ItemStack stack) {
	}

	private record Flight(ItemStack stack, Slot from, Slot to, long start) {
	}

	private AbstractContainerMenu menu;
	private ItemStack[] last;
	private final List<Flight> flights = new ArrayList<>();

	public SmoothSwap() {
		super("SmoothSwap", "Items glide between inventory slots", Category.VISUALS);
	}

	/** Before the slots are drawn: finds the stacks that moved since the last frame. */
	public void update(AbstractContainerMenu menu) {
		List<Slot> slots = menu.slots;
		if (menu != this.menu || last == null || last.length != slots.size()) {
			this.menu = menu;
			flights.clear();
			snapshot(slots);
			return;
		}
		List<Change> lost = new ArrayList<>(), gained = new ArrayList<>();
		for (int i = 0; i < slots.size(); i++) {
			ItemStack before = last[i], now = slots.get(i).getItem();
			if (ItemStack.matches(before, now)) continue;
			boolean same = ItemStack.isSameItemSameComponents(before, now);
			if (!before.isEmpty() && (!same || now.getCount() < before.getCount())) {
				lost.add(new Change(i, before.copyWithCount(same ? before.getCount() - now.getCount() : before.getCount())));
			}
			if (!now.isEmpty() && (!same || now.getCount() > before.getCount())) {
				gained.add(new Change(i, now.copyWithCount(same ? now.getCount() - before.getCount() : now.getCount())));
			}
		}
		// a whole new container content (sorting, server resync) is not animated
		if (!gained.isEmpty() && lost.size() + gained.size() <= 24) {
			long now = System.currentTimeMillis();
			for (Change g : gained) {
				for (Change l : lost) {
					if (l.slot() != g.slot() && ItemStack.isSameItemSameComponents(l.stack(), g.stack())) {
						flights.add(new Flight(g.stack(), slots.get(l.slot()), slots.get(g.slot()), now));
						break;
					}
				}
			}
		}
		snapshot(slots);
	}

	private void snapshot(List<Slot> slots) {
		last = new ItemStack[slots.size()];
		for (int i = 0; i < slots.size(); i++) last[i] = slots.get(i).getItem().copy();
	}

	/** The target slot of a flight stays empty until the item lands. */
	public boolean hides(Slot slot) {
		for (Flight f : flights) if (f.to() == slot) return true;
		return false;
	}

	/** After the slots: the flying items, in the slot coordinates of the screen. */
	public void render(GuiGraphics ctx) {
		long now = System.currentTimeMillis();
		float length = duration.getFloat();
		flights.removeIf(f -> now - f.start() >= length);
		for (Flight f : flights) {
			float t = (now - f.start()) / length;
			float e = 1f - (1f - t) * (1f - t) * (1f - t);
			float x = f.from().x + (f.to().x - f.from().x) * e, y = f.from().y + (f.to().y - f.from().y) * e;
			ctx.pose().pushMatrix();
			ctx.pose().translate(x, y);
			ctx.renderItem(f.stack(), 0, 0);
			ctx.renderItemDecorations(mc.font, f.stack(), 0, 0);
			ctx.pose().popMatrix();
		}
	}
}
