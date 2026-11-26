package com.zerovisual.gui;

import com.zerovisual.render.DrawContext;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * A one-line text input for the menus (1.8.9 has no text field that takes the menu font).
 * Typing goes to the end; Backspace, Ctrl+Backspace, Ctrl+V and Ctrl+A (clear on typing) work.
 */
public class TextField extends ZvScreen.Widget {
	public interface Filter {
		boolean test(String text);
	}

	private String text = "";
	private String placeholder = "";
	private int maxLength = 32;
	private Filter filter;
	private boolean focused, selectedAll, password, editable = true;
	private int color = Theme.TEXT;

	public TextField(float x, float y, float width, float height) {
		super(x, y, width, height);
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text.length() > maxLength ? text.substring(0, maxLength) : text;
	}

	public void setPlaceholder(String placeholder) {
		this.placeholder = placeholder;
	}

	public void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
	}

	public void setTextPredicate(Filter filter) {
		this.filter = filter;
	}

	public void setEditableColor(int rgb) {
		this.color = 0xFF000000 | rgb;
	}

	/** A read-only field still selects and copies, but takes no typing. */
	public void setEditable(boolean editable) {
		this.editable = editable;
	}

	/** Shows dots instead of the text. */
	public void setPassword(boolean password) {
		this.password = password;
	}

	public boolean isFocused() {
		return focused;
	}

	public void setFocused(boolean focused) {
		this.focused = focused;
		selectedAll = false;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY) {
		if (!visible) return;
		String shown = password ? repeat('•', text.length()) : text;
		float maxW = width - 4;
		// keep the end in view
		while (shown.length() > 0 && Render2D.textWidthF(shown) > maxW) shown = shown.substring(1);
		if (text.isEmpty() && !focused) {
			Render2D.text(ctx, placeholder, x, y, Theme.TEXT_DIM);
		} else {
			if (selectedAll && !text.isEmpty()) Render2D.rect(ctx, x - 1, y - 1, Render2D.textWidthF(shown) + 2, 10, 1, com.zerovisual.render.ColorUtil.withAlpha(Theme.accent(), 0.35f));
			Render2D.text(ctx, shown, x, y, color);
		}
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			Render2D.rect(ctx, x + Render2D.textWidthF(text.isEmpty() ? "" : shown) + 1, y - 0.5f, 1, 9, 0, color);
		}
	}

	private static String repeat(char c, int n) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < n; i++) b.append(c);
		return b.toString();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		setFocused(visible && active && mouseX >= x - 4 && mouseX < x + width && mouseY >= y - 6 && mouseY < y + height + 6);
		return focused;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (!focused) return false;
		boolean ctrl = (modifiers & ZvScreen.MOD_CONTROL) != 0;
		if (key == Keyboard.KEY_BACK && editable) {
			if (selectedAll || ctrl) text = "";
			else if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
			selectedAll = false;
			return true;
		}
		if (ctrl && key == Keyboard.KEY_A) {
			selectedAll = true;
			return true;
		}
		if (ctrl && key == Keyboard.KEY_C) {
			GuiScreen.setClipboardString(text);
			return true;
		}
		if (ctrl && key == Keyboard.KEY_V && editable) {
			insert(GuiScreen.getClipboardString().replaceAll("[\r\n\t]", ""));
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(char chr) {
		if (!focused || !editable || chr < ' ' || chr == 127) return false;
		insert(String.valueOf(chr));
		return true;
	}

	private void insert(String typed) {
		String next = (selectedAll ? "" : text) + typed;
		if (next.length() > maxLength) next = next.substring(0, maxLength);
		if (filter != null && !filter.test(next)) return;
		text = next;
		selectedAll = false;
	}
}
