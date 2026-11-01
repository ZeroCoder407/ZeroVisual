package com.zerovisual.gui;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.account.AccountManager;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;

/** Offline account switcher, opened from the title screen. */
public class AccountScreen extends Screen {
	private static final float W = 280;
	private static final float ROW_H = 24;
	private static final int VISIBLE_ROWS = 6;

	private final Screen parent;
	private final AccountManager accounts;
	private final Anim openAnim = new Anim(10f, 0f);
	private final Map<String, Anim> rowHover = new HashMap<>();
	private final Map<String, Anim> buttonHover = new HashMap<>();

	private EditBox nameField;
	private String status = "";
	private int statusColor = Theme.TEXT_DIM;
	private int scroll;

	public AccountScreen(Screen parent) {
		super(Component.literal("Accounts"));
		this.parent = parent;
		this.accounts = ZeroVisualClient.get().accounts();
	}

	private float panelX() {
		return (width - W) / 2f;
	}

	private float panelH() {
		return 76 + VISIBLE_ROWS * ROW_H + 40;
	}

	private float panelY() {
		return (height - panelH()) / 2f;
	}

	private float listY() {
		return panelY() + 76;
	}

	@Override
	protected void init() {
		float x = panelX(), y = panelY();
		nameField = new EditBox(font, (int) x + 22, (int) y + 50, 150, 10, Component.literal("Nickname"));
		nameField.setBordered(false);
		nameField.setMaxLength(16);
		// keep only the characters allowed in a name
		nameField.setResponder(s -> {
			String clean = s.replaceAll("[^A-Za-z0-9_]", "");
			if (!clean.equals(s)) nameField.setValue(clean);
		});
		nameField.setHint(Component.translatable("zerovisual.account.placeholder").withStyle(ChatFormatting.DARK_GRAY));
		nameField.setTextColor(Theme.TEXT & 0xFFFFFF);
		addRenderableWidget(nameField);
		setInitialFocus(nameField);
	}

	// ---- Rendering ----

	@Override
	public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		renderPanorama(ctx, delta);
		ctx.fillGradient(0, 0, width, height, 0x40000000, 0x80000000);

		float o = openAnim.update(1f);
		Render2D.setAlpha(o);
		float x = panelX(), y = panelY(), h = panelH();
		Render2D.shadow(ctx, x, y + 4, W, h, 12, 18, 0xA0000000);
		Render2D.rect(ctx, x, y, W, h, 12, Theme.BACKGROUND);
		Render2D.outline(ctx, x, y, W, h, 12, 1, 0x18FFFFFF);

		// header
		Render2D.gradientV(ctx, x + 14, y + 13, 14, 14, 4, Theme.accentLight(), Theme.accent());
		Render2D.centeredText(ctx, "☺", x + 21.5f, y + 16, 0xFFFFFFFF);
		Render2D.text(ctx, I18n.get("zerovisual.account.title"), x + 36, y + 12, Theme.TEXT);
		Render2D.text(ctx, I18n.get("zerovisual.account.current", accounts.currentName()), x + 36, y + 22, Theme.TEXT_DIM);

		// input box (the text field itself is drawn on top by the widget)
		boolean focused = nameField != null && nameField.isFocused();
		Render2D.rect(ctx, x + 14, y + 43, 166, 22, 7, Theme.SURFACE);
		Render2D.outline(ctx, x + 14, y + 43, 166, 22, 7, 1, focused ? Theme.accent() : 0x18FFFFFF);
		Render2D.setAlpha(1f);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		Render2D.setAlpha(openAnim.get());

		float x = panelX(), y = panelY();
		button(ctx, "add", I18n.get("zerovisual.button.add"), x + 186, y + 43, 80, 22, mouseX, mouseY, true);

		renderList(ctx, mouseX, mouseY);

		float by = y + panelH() - 32;
		button(ctx, "random", I18n.get("zerovisual.account.random"), x + 14, by, 100, 20, mouseX, mouseY, false);
		if (!accounts.isOriginal()) {
			button(ctx, "restore", I18n.get("zerovisual.account.restore", accounts.originalName()), x + 118, by, 92, 20, mouseX, mouseY, false);
		}
		button(ctx, "done", I18n.get("zerovisual.button.done"), x + W - 14 - 52, by, 52, 20, mouseX, mouseY, true);

		if (!status.isEmpty()) Render2D.centeredText(ctx, status, width / 2f, y + panelH() + 8, statusColor);
		Render2D.setAlpha(1f);
	}

	private void renderList(GuiGraphics ctx, int mouseX, int mouseY) {
		float x = panelX() + 14, y = listY(), w = W - 28;
		List<String> list = accounts.getAccounts();
		scroll = Math.max(0, Math.min(scroll, list.size() - VISIBLE_ROWS));

		if (list.isEmpty()) {
			Render2D.centeredText(ctx, I18n.get("zerovisual.account.empty"), x + w / 2, y + VISIBLE_ROWS * ROW_H / 2 - 8, Theme.TEXT_DIM);
			Render2D.centeredText(ctx, I18n.get("zerovisual.account.empty.hint"), x + w / 2, y + VISIBLE_ROWS * ROW_H / 2 + 4, 0xFF4E4E5C);
			return;
		}

		String current = accounts.currentName();
		for (int i = 0; i < VISIBLE_ROWS && i + scroll < list.size(); i++) {
			String name = list.get(i + scroll);
			float ry = y + i * ROW_H;
			boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY < ry + ROW_H - 2;
			float hv = rowHover.computeIfAbsent(name, n -> new Anim(18f, 0f)).update(hovered);
			boolean active = name.equals(current);

			int bg = active ? ColorUtil.withAlpha(Theme.accent(), 0.22f) : ColorUtil.withAlpha(0xFFFFFF, 0.025f + 0.04f * hv);
			Render2D.rect(ctx, x, ry, w, ROW_H - 3, 6, bg);
			if (active) Render2D.rect(ctx, x + 3, ry + 6, 2, ROW_H - 15, 1, Theme.accentLight());
			PlayerFaceRenderer.draw(ctx, DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(name)), (int) x + 9, (int) ry + 3, 15);
			Render2D.text(ctx, name, x + 30, ry + 7, active ? Theme.TEXT : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv));

			if (active) {
				String activeLabel = I18n.get("zerovisual.account.active");
				Render2D.text(ctx, activeLabel, x + w - 10 - Render2D.textWidth(activeLabel), ry + 7, Theme.accentLight());
			} else if (hv > 0.05f) {
				boolean overDelete = mouseX >= x + w - 20 && mouseX <= x + w - 4;
				Render2D.text(ctx, "✕", x + w - 14, ry + 7, ColorUtil.fade(overDelete ? 0xFFFF5C6C : Theme.TEXT_DIM, hv));
			}
		}

		if (list.size() > VISIBLE_ROWS) {
			float trackH = VISIBLE_ROWS * ROW_H - 3;
			float thumbH = Math.max(12, trackH * VISIBLE_ROWS / list.size());
			float thumbY = y + (trackH - thumbH) * scroll / (list.size() - VISIBLE_ROWS);
			Render2D.rect(ctx, x + w + 4, thumbY, 2, thumbH, 1, 0x40FFFFFF);
		}
	}

	private void button(GuiGraphics ctx, String id, String label, float x, float y, float w, float h, int mx, int my, boolean primary) {
		boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
		float hv = buttonHover.computeIfAbsent(id, k -> new Anim(18f, 0f)).update(hovered);
		if (primary) {
			Render2D.gradientH(ctx, x, y, w, h, 6, ColorUtil.brighten(Theme.accent(), 1f + 0.15f * hv), ColorUtil.brighten(Theme.accentLight(), 1f + 0.1f * hv));
		} else {
			Render2D.rect(ctx, x, y, w, h, 6, ColorUtil.lerp(0xFF17171F, 0xFF22222C, hv));
		}
		Render2D.centeredText(ctx, label, x + w / 2, y + (h - 8) / 2 + 0.5f, primary ? 0xFFFFFFFF : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv));
	}

	private static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	// ---- Input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int button = event.button();
		float x = panelX(), y = panelY();
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			if (inside(mx, my, x + 186, y + 43, 80, 22)) {
				addFromField();
				return true;
			}
			float by = y + panelH() - 32;
			if (inside(mx, my, x + 14, by, 100, 20)) {
				nameField.setValue(accounts.randomName());
				setFocused(nameField);
				return true;
			}
			if (!accounts.isOriginal() && inside(mx, my, x + 118, by, 92, 20)) {
				accounts.restoreOriginal();
				setStatus(I18n.get("zerovisual.account.restored", accounts.currentName()), Theme.TEXT_MUTED);
				return true;
			}
			if (inside(mx, my, x + W - 66, by, 52, 20)) {
				onClose();
				return true;
			}

			float lx = x + 14, ly = listY(), lw = W - 28;
			List<String> list = accounts.getAccounts();
			for (int i = 0; i < VISIBLE_ROWS && i + scroll < list.size(); i++) {
				float ry = ly + i * ROW_H;
				if (!inside(mx, my, lx, ry, lw, ROW_H - 3)) continue;
				String name = list.get(i + scroll);
				boolean active = name.equals(accounts.currentName());
				if (!active && mx >= lx + lw - 20) {
					accounts.remove(name);
					setStatus(I18n.get("zerovisual.account.removed", name), Theme.TEXT_DIM);
				} else if (!active) {
					accounts.login(name);
					setStatus(I18n.get("zerovisual.account.logged_in", name), Theme.accentLight());
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= (int) Math.signum(verticalAmount);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key(), scanCode = event.scancode(), modifiers = event.modifiers();
		if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) && nameField.isFocused()) {
			addFromField();
			return true;
		}
		return super.keyPressed(event);
	}

	private void addFromField() {
		String name = nameField.getValue().trim();
		if (!AccountManager.VALID_NAME.matcher(name).matches()) {
			setStatus(I18n.get("zerovisual.account.invalid"), 0xFFFF5C6C);
			return;
		}
		if (!accounts.add(name)) {
			setStatus(I18n.get("zerovisual.account.duplicate"), 0xFFFFB454);
			return;
		}
		nameField.setValue("");
		scroll = Math.max(0, accounts.getAccounts().size() - VISIBLE_ROWS);
		setStatus(I18n.get("zerovisual.account.added", name), Theme.TEXT_MUTED);
	}

	private void setStatus(String text, int color) {
		status = text;
		statusColor = color;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
