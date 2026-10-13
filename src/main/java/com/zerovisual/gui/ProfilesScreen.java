package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.config.ConfigManager;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Save the current settings as a named profile, load or delete profiles. Opened from the ClickGUI. */
public class ProfilesScreen extends Screen {
	private static final float W = 280;
	private static final float ROW_H = 24;
	private static final int VISIBLE_ROWS = 6;

	private final Screen parent;
	private final ConfigManager config;
	private final Anim openAnim = new Anim(10f, 0f);
	private final Map<String, Anim> hover = new HashMap<>();

	private TextFieldWidget nameField;
	private List<String> profiles = List.of();
	private String status = "";
	private int statusColor = Theme.TEXT_DIM;
	private int scroll;

	public ProfilesScreen(Screen parent) {
		super(Text.literal("Profiles"));
		this.parent = parent;
		this.config = ZeroVisualClient.get().config();
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
		profiles = config.listProfiles();
		float x = panelX(), y = panelY();
		nameField = new TextFieldWidget(textRenderer, (int) x + 22, (int) y + 50, 150, 10, Text.literal("Profile"));
		nameField.setDrawsBackground(false);
		nameField.setMaxLength(24);
		nameField.setTextPredicate(s -> s.isEmpty() || ConfigManager.isValidProfileName(s));
		nameField.setPlaceholder(Text.translatable("zerovisual.profiles.placeholder").formatted(Formatting.DARK_GRAY));
		nameField.setEditableColor(Theme.TEXT & 0xFFFFFF);
		addDrawableChild(nameField);
		setInitialFocus(nameField);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		float o = openAnim.update(1f);
		Render2D.setAlpha(o);
		float x = panelX(), y = panelY(), h = panelH();
		Render2D.shadow(ctx, x, y + 4, W, h, 12, 18, 0xA0000000);
		Render2D.rect(ctx, x, y, W, h, 12, Theme.BACKGROUND);
		Render2D.outline(ctx, x, y, W, h, 12, 1, 0x18FFFFFF);

		Render2D.gradientV(ctx, x + 14, y + 13, 14, 14, 4, Theme.accentLight(), Theme.accent());
		Render2D.centeredText(ctx, "☰", x + 21.5f, y + 16, 0xFFFFFFFF);
		Render2D.text(ctx, I18n.translate("zerovisual.profiles.title"), x + 36, y + 12, Theme.TEXT);
		Render2D.text(ctx, I18n.translate("zerovisual.profiles.subtitle"), x + 36, y + 22, Theme.TEXT_DIM);

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
		button(ctx, "save", I18n.translate("zerovisual.button.save"), x + 186, y + 43, 80, 22, mouseX, mouseY, true);
		renderList(ctx, mouseX, mouseY);
		button(ctx, "done", I18n.translate("zerovisual.button.done"), x + W - 66, y + panelH() - 32, 52, 20, mouseX, mouseY, true);
		Render2D.text(ctx, I18n.translate("zerovisual.profiles.hint"), x + 14, y + panelH() - 26, 0xFF4E4E5C);
		if (!status.isEmpty()) Render2D.centeredText(ctx, status, width / 2f, y + panelH() + 8, statusColor);
		Render2D.setAlpha(1f);
	}

	private void renderList(DrawContext ctx, int mouseX, int mouseY) {
		float x = panelX() + 14, y = listY(), w = W - 28;
		scroll = Math.max(0, Math.min(scroll, profiles.size() - VISIBLE_ROWS));
		if (profiles.isEmpty()) {
			Render2D.centeredText(ctx, I18n.translate("zerovisual.profiles.empty"), x + w / 2, y + VISIBLE_ROWS * ROW_H / 2 - 8, Theme.TEXT_DIM);
			Render2D.centeredText(ctx, I18n.translate("zerovisual.profiles.empty.hint"), x + w / 2, y + VISIBLE_ROWS * ROW_H / 2 + 4, 0xFF4E4E5C);
			return;
		}
		for (int i = 0; i < VISIBLE_ROWS && i + scroll < profiles.size(); i++) {
			String name = profiles.get(i + scroll);
			float ry = y + i * ROW_H;
			boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY < ry + ROW_H - 3;
			float hv = hover.computeIfAbsent(name, n -> new Anim(18f, 0f)).update(hovered);
			Render2D.rect(ctx, x, ry, w, ROW_H - 3, 6, ColorUtil.withAlpha(0xFFFFFF, 0.025f + 0.045f * hv));
			Render2D.circle(ctx, x + 12, ry + (ROW_H - 3) / 2, 2.5f, ColorUtil.lerp(Theme.TEXT_DIM, Theme.accent(), hv));
			Render2D.text(ctx, name, x + 22, ry + 7, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv));
			if (hv > 0.05f) {
				boolean overDelete = mouseX >= x + w - 20;
				Render2D.text(ctx, "✕", x + w - 14, ry + 7, ColorUtil.fade(overDelete ? 0xFFFF5C6C : Theme.TEXT_DIM, hv));
			}
		}
	}

	private void button(DrawContext ctx, String id, String label, float x, float y, float w, float h, int mx, int my, boolean primary) {
		boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
		float hv = hover.computeIfAbsent("#" + id, k -> new Anim(18f, 0f)).update(hovered);
		if (primary) {
			Render2D.gradientH(ctx, x, y, w, h, 6, ColorUtil.brighten(Theme.accent(), 1f + 0.15f * hv), ColorUtil.brighten(Theme.accentLight(), 1f + 0.1f * hv));
		} else {
			Render2D.rect(ctx, x, y, w, h, 6, ColorUtil.lerp(0xFF17171F, 0xFF22222C, hv));
		}
		Render2D.centeredText(ctx, label, x + w / 2, y + (h - 8) / 2 + 0.5f, 0xFFFFFFFF);
	}

	private static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		float x = panelX(), y = panelY();
		if (button == 0) {
			if (inside(mx, my, x + 186, y + 43, 80, 22)) {
				saveFromField();
				return true;
			}
			if (inside(mx, my, x + W - 66, y + panelH() - 32, 52, 20)) {
				close();
				return true;
			}
			float lx = x + 14, ly = listY(), lw = W - 28;
			for (int i = 0; i < VISIBLE_ROWS && i + scroll < profiles.size(); i++) {
				float ry = ly + i * ROW_H;
				if (!inside(mx, my, lx, ry, lw, ROW_H - 3)) continue;
				String name = profiles.get(i + scroll);
				if (mx >= lx + lw - 20) {
					config.deleteProfile(name);
					profiles = config.listProfiles();
					setStatus(I18n.translate("zerovisual.profiles.deleted", name), Theme.TEXT_DIM);
				} else if (config.loadProfile(name)) {
					setStatus(I18n.translate("zerovisual.profiles.loaded", name), Theme.accentLight());
				} else {
					setStatus(I18n.translate("zerovisual.profiles.load_failed", name), 0xFFFF5C6C);
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
			saveFromField();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void saveFromField() {
		String name = nameField.getText().trim();
		if (!ConfigManager.isValidProfileName(name)) {
			setStatus(I18n.translate("zerovisual.profiles.invalid"), 0xFFFF5C6C);
			return;
		}
		boolean existed = profiles.contains(name);
		if (config.saveProfile(name)) {
			profiles = config.listProfiles();
			nameField.setText("");
			setStatus(I18n.translate(existed ? "zerovisual.profiles.overwritten" : "zerovisual.profiles.saved", name), Theme.TEXT_MUTED);
		} else {
			setStatus(I18n.translate("zerovisual.profiles.save_failed"), 0xFFFF5C6C);
		}
	}

	private void setStatus(String text, int color) {
		status = text;
		statusColor = color;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
