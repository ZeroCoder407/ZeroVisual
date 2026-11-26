package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.DrawContext;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Items glide from slot to slot in inventories (shift click, number keys, double click) instead of jumping.
 * Each frame the slots are compared with the previous frame; a stack that left one slot and showed up in another
 * flies between them, and the target slot is hidden until it lands (GuiContainerMixin).
 */
public class SmoothSwap extends Module {
	public final NumberSetting duration = add(new NumberSetting("Duration ms", 160, 60, 400, 10));

	private static final class Change {
		final int slot;
		final ItemStack stack;

		Change(int slot, ItemStack stack) {
			this.slot = slot;
			this.stack = stack;
		}
	}

	private static final class Flight {
		final ItemStack stack;
		final Slot from, to;
		final long start;

		Flight(ItemStack stack, Slot from, Slot to, long start) {
			this.stack = stack;
			this.from = from;
			this.to = to;
			this.start = start;
		}
	}

	private Container handler;
	private ItemStack[] last;
	private final List<Flight> flights = new ArrayList<Flight>();

	public SmoothSwap() {
		super("SmoothSwap", "Items glide between inventory slots", Category.VISUALS);
	}

	/** Before the slots are drawn: finds the stacks that moved since the last frame. */
	public void update(Container handler) {
		List<Slot> slots = handler.inventorySlots;
		if (handler != this.handler || last == null || last.length != slots.size()) {
			this.handler = handler;
			flights.clear();
			snapshot(slots);
			return;
		}
		List<Change> lost = new ArrayList<Change>(), gained = new ArrayList<Change>();
		for (int i = 0; i < slots.size(); i++) {
			ItemStack before = last[i], now = slots.get(i).getStack();
			if (ItemStack.areItemStacksEqual(before, now)) continue;
			boolean same = sameKind(before, now);
			int countBefore = before == null ? 0 : before.stackSize, countNow = now == null ? 0 : now.stackSize;
			if (before != null && (!same || countNow < countBefore)) lost.add(new Change(i, withCount(before, same ? countBefore - countNow : countBefore)));
			if (now != null && (!same || countNow > countBefore)) gained.add(new Change(i, withCount(now, same ? countNow - countBefore : countNow)));
		}
		// a whole new container content (sorting, server resync) is not animated
		if (!gained.isEmpty() && lost.size() + gained.size() <= 24) {
			long now = System.currentTimeMillis();
			for (Change g : gained) {
				for (Change l : lost) {
					if (l.slot != g.slot && sameKind(l.stack, g.stack)) {
						flights.add(new Flight(g.stack, slots.get(l.slot), slots.get(g.slot), now));
						break;
					}
				}
			}
		}
		snapshot(slots);
	}

	private static boolean sameKind(ItemStack a, ItemStack b) {
		return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
	}

	private static ItemStack withCount(ItemStack stack, int count) {
		ItemStack copy = stack.copy();
		copy.stackSize = count;
		return copy;
	}

	private void snapshot(List<Slot> slots) {
		last = new ItemStack[slots.size()];
		for (int i = 0; i < slots.size(); i++) {
			ItemStack stack = slots.get(i).getStack();
			last[i] = stack == null ? null : stack.copy();
		}
	}

	/** The target slot of a flight stays empty until the item lands. */
	public boolean hides(Slot slot) {
		for (Flight f : flights) if (f.to == slot) return true;
		return false;
	}

	/** After the slots: the flying items, in the slot coordinates of the screen. */
	public void render(DrawContext ctx) {
		long now = System.currentTimeMillis();
		float length = duration.getFloat();
		Iterator<Flight> it = flights.iterator();
		while (it.hasNext()) if (now - it.next().start >= length) it.remove();
		for (Flight f : flights) {
			float t = (now - f.start) / length;
			float e = 1f - (1f - t) * (1f - t) * (1f - t);
			float x = f.from.xDisplayPosition + (f.to.xDisplayPosition - f.from.xDisplayPosition) * e;
			float y = f.from.yDisplayPosition + (f.to.yDisplayPosition - f.from.yDisplayPosition) * e;
			GlStateManager.pushMatrix();
			GlStateManager.translate(x, y, 250);
			ctx.drawItem(f.stack, 0, 0);
			ctx.drawStackOverlay(f.stack, 0, 0);
			GlStateManager.popMatrix();
		}
	}
}
