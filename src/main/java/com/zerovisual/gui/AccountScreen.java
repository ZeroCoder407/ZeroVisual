package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.account.AccountManager;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Uuids;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	private TextFieldWidget nameField;
	private String status = "";
	private int statusColor = Theme.TEXT_DIM;
	private int scroll;

	public AccountScreen(Screen parent) {
		super(Text.literal("Accounts"));
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
		nameField = new TextFieldWidget(textRenderer, (int) x + 22, (int) y + 50, 150, 10, Text.literal("Nickname"));
		nameField.setDrawsBackground(false);
		nameField.setMaxLength(16);
		nameField.setTextPredicate(s -> s.matches("[A-Za-z0-9_]*"));
		nameField.setPlaceholder(Text.literal("Ник (3-16 символов)").formatted(Formatting.DARK_GRAY));
		nameField.setEditableColor(Theme.TEXT & 0xFFFFFF);
		addDrawableChild(nameField);
		setInitialFocus(nameField);
	}

	// ---- Rendering ----

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		renderPanoramaBackground(ctx, delta);
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
		Render2D.text(ctx, "Аккаунты", x + 36, y + 12, Theme.TEXT);
		Render2D.text(ctx, "Текущий: " + accounts.currentName(), x + 36, y + 22, Theme.TEXT_DIM);

		// input box (the text field itself is drawn on top by the widget)
		boolean focused = nameField != null && nameField.isFocused();
		Render2D.rect(ctx, x + 14, y + 43, 166, 22, 7, Theme.SURFACE);
		Render2D.outline(ctx, x + 14, y + 43, 166, 22, 7, 1, focused ? Theme.accent() : 0x18FFFFFF);
		Render2D.setAlpha(1f);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		Render2D.setAlpha(openAnim.get());

		float x = panelX(), y = panelY();
		button(ctx, "add", "Добавить", x + 186, y + 43, 80, 22, mouseX, mouseY, true);

		renderList(ctx, mouseX, mouseY);

		float by = y + panelH() - 32;
		button(ctx, "random", "Случайный ник", x + 14, by, 100, 20, mouseX, mouseY, false);
		if (!accounts.isOriginal()) {
			button(ctx, "restore", "Вернуть " + accounts.originalName(), x + 118, by, 92, 20, mouseX, mouseY, false);
		}
		button(ctx, "done", "Готово", x + W - 14 - 52, by, 52, 20, mouseX, mouseY, true);

		if (!status.isEmpty()) Render2D.centeredText(ctx, status, width / 2f, y + panelH() + 8, statusColor);
		Render2D.setAlpha(1f);
	}

	private void renderList(DrawContext ctx, int mouseX, int mouseY) {
		float x = panelX() + 14, y = listY(), w = W - 28;
		List<String> list = accounts.getAccounts();
		scroll = Math.max(0, Math.min(scroll, list.size() - VISIBLE_ROWS));

		if (list.isEmpty()) {
			Render2D.centeredText(ctx, "Пока нет аккаунтов", x + w / 2, y + VISIBLE_ROWS * ROW_H / 2 - 8, Theme.TEXT_DIM);
			Render2D.centeredText(ctx, "Введите ник и нажмите «Добавить»", x + w / 2, y + VISIBLE_ROWS * ROW_H / 2 + 4, 0xFF4E4E5C);
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

			ctx.draw();
			PlayerSkinDrawer.draw(ctx, DefaultSkinHelper.getSkinTextures(Uuids.getOfflinePlayerUuid(name)), (int) x + 9, (int) ry + 3, 15);
			Render2D.text(ctx, name, x + 30, ry + 7, active ? Theme.TEXT : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv));

			if (active) {
				Render2D.text(ctx, "активен", x + w - 10 - Render2D.textWidth("активен"), ry + 7, Theme.accentLight());
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

	private void button(DrawContext ctx, String id, String label, float x, float y, float w, float h, int mx, int my, boolean primary) {
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
	public boolean mouseClicked(double mx, double my, int button) {
		float x = panelX(), y = panelY();
		if (button == 0) {
			if (inside(mx, my, x + 186, y + 43, 80, 22)) {
				addFromField();
				return true;
			}
			float by = y + panelH() - 32;
			if (inside(mx, my, x + 14, by, 100, 20)) {
				nameField.setText(accounts.randomName());
				setFocused(nameField);
				return true;
			}
			if (!accounts.isOriginal() && inside(mx, my, x + 118, by, 92, 20)) {
				accounts.restoreOriginal();
				setStatus("Возвращён аккаунт " + accounts.currentName(), Theme.TEXT_MUTED);
				return true;
			}
			if (inside(mx, my, x + W - 66, by, 52, 20)) {
				close();
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
					setStatus("Удалён " + name, Theme.TEXT_DIM);
				} else if (!active) {
					accounts.login(name);
					setStatus("Вы вошли как " + name, Theme.accentLight());
				}
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= (int) Math.signum(verticalAmount);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && nameField.isFocused()) {
			addFromField();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void addFromField() {
		String name = nameField.getText().trim();
		if (!AccountManager.VALID_NAME.matcher(name).matches()) {
			setStatus("Ник: 3-16 символов, латиница, цифры и _", 0xFFFF5C6C);
			return;
		}
		if (!accounts.add(name)) {
			setStatus("Такой ник уже есть в списке", 0xFFFFB454);
			return;
		}
		nameField.setText("");
		scroll = Math.max(0, accounts.getAccounts().size() - VISIBLE_ROWS);
		setStatus("Добавлен " + name + " (нажмите, чтобы войти)", Theme.TEXT_MUTED);
	}

	private void setStatus(String text, int color) {
		status = text;
		statusColor = color;
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
