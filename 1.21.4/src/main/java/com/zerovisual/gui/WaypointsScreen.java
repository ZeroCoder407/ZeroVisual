package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.waypoint.WaypointManager;
import com.zerovisual.waypoint.WaypointManager.Waypoint;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Waypoint list of the current world. Add at your position, LMB on a row: show / hide,
 * RMB on a row: change color, ✕: delete.
 */
public class WaypointsScreen extends Screen {
	private static final float W = 300;
	private static final float ROW_H = 24;
	private static final int VISIBLE_ROWS = 7;

	@Nullable
	private final Screen parent;
	private final WaypointManager manager;
	private final Anim openAnim = new Anim(10f, 0f);
	private final Map<Object, Anim> hover = new HashMap<>();

	private TextFieldWidget nameField;
	private int scroll;

	public WaypointsScreen(@Nullable Screen parent) {
		super(Text.literal("Waypoints"));
		this.parent = parent;
		this.manager = ZeroVisualClient.get().waypoints();
	}

	private float panelX() {
		return (width - W) / 2f;
	}

	private float panelH() {
		return 76 + VISIBLE_ROWS * ROW_H + 36;
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
		nameField = new TextFieldWidget(textRenderer, (int) x + 22, (int) y + 50, 170, 10, Text.literal("Name"));
		nameField.setDrawsBackground(false);
		nameField.setMaxLength(32);
		nameField.setPlaceholder(Text.translatable("zerovisual.waypoints.placeholder").formatted(Formatting.DARK_GRAY));
		nameField.setEditableColor(Theme.TEXT & 0xFFFFFF);
		addDrawableChild(nameField);
		setInitialFocus(nameField);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		Render2D.setAlpha(openAnim.update(1f));
		float x = panelX(), y = panelY(), h = panelH();
		Render2D.shadow(ctx, x, y + 4, W, h, 12, 18, 0xA0000000);
		Render2D.rect(ctx, x, y, W, h, 12, Theme.BACKGROUND);
		Render2D.outline(ctx, x, y, W, h, 12, 1, 0x18FFFFFF);

		Render2D.gradientV(ctx, x + 14, y + 13, 14, 14, 4, Theme.accentLight(), Theme.accent());
		Render2D.centeredText(ctx, "◆", x + 21.5f, y + 16, 0xFFFFFFFF);
		Render2D.text(ctx, I18n.translate("zerovisual.waypoints.title"), x + 36, y + 12, Theme.TEXT);
		String world = WaypointManager.worldKey().replaceFirst("^(sp|mp):", "");
		Render2D.text(ctx, world + "  •  " + WaypointManager.currentDimension().replace("minecraft:", ""), x + 36, y + 22, Theme.TEXT_DIM);

		boolean focused = nameField != null && nameField.isFocused();
		Render2D.rect(ctx, x + 14, y + 43, 186, 22, 7, Theme.SURFACE);
		Render2D.outline(ctx, x + 14, y + 43, 186, 22, 7, 1, focused ? Theme.accent() : 0x18FFFFFF);
		Render2D.setAlpha(1f);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		Render2D.setAlpha(openAnim.get());
		float x = panelX(), y = panelY();
		button(ctx, "add", I18n.translate("zerovisual.button.add"), x + 206, y + 43, 80, 22, mouseX, mouseY);
		renderList(ctx, mouseX, mouseY);
		Render2D.text(ctx, I18n.translate("zerovisual.waypoints.hint"), x + 14, y + panelH() - 22, 0xFF4E4E5C);
		Render2D.setAlpha(1f);
	}

	private void renderList(DrawContext ctx, int mouseX, int mouseY) {
		float x = panelX() + 14, y = listY(), w = W - 28;
		List<Waypoint> list = manager.current();
		scroll = Math.max(0, Math.min(scroll, list.size() - VISIBLE_ROWS));
		if (list.isEmpty()) {
			Render2D.centeredText(ctx, I18n.translate("zerovisual.waypoints.empty"), x + w / 2, y + VISIBLE_ROWS * ROW_H / 2 - 8, Theme.TEXT_DIM);
			Render2D.centeredText(ctx, I18n.translate("zerovisual.waypoints.empty.hint"), x + w / 2, y + VISIBLE_ROWS * ROW_H / 2 + 4, 0xFF4E4E5C);
			return;
		}
		String dimension = WaypointManager.currentDimension();
		for (int i = 0; i < VISIBLE_ROWS && i + scroll < list.size(); i++) {
			Waypoint wp = list.get(i + scroll);
			float ry = y + i * ROW_H;
			boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY < ry + ROW_H - 3;
			float hv = hover.computeIfAbsent(wp, k -> new Anim(18f, 0f)).update(hovered);
			float dim = wp.visible ? 1f : 0.45f;

			Render2D.rect(ctx, x, ry, w, ROW_H - 3, 6, ColorUtil.withAlpha(0xFFFFFF, 0.025f + 0.045f * hv));
			Render2D.circle(ctx, x + 11, ry + (ROW_H - 3) / 2, 3.5f, ColorUtil.fade(wp.color, dim));
			Render2D.text(ctx, wp.name, x + 22, ry + 3, ColorUtil.fade(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), dim));

			String info = wp.x + " " + wp.y + " " + wp.z;
			if (wp.dimension.equals(dimension) && client != null && client.player != null) {
				info += "  •  " + Math.round(Math.sqrt(client.player.squaredDistanceTo(wp.x + 0.5, wp.y, wp.z + 0.5))) + "m";
			} else {
				info += "  •  " + wp.dimension.replace("minecraft:", "");
			}
			Render2D.scaledText(ctx, info, x + 22, ry + 12.5f, 0.75f, ColorUtil.fade(Theme.TEXT_DIM, dim));
			if (hv > 0.05f) {
				boolean overDelete = mouseX >= x + w - 20;
				Render2D.text(ctx, "✕", x + w - 14, ry + 7, ColorUtil.fade(overDelete ? 0xFFFF5C6C : Theme.TEXT_DIM, hv));
			}
		}
	}

	private void button(DrawContext ctx, String id, String label, float x, float y, float w, float h, int mx, int my) {
		boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
		float hv = hover.computeIfAbsent("#" + id, k -> new Anim(18f, 0f)).update(hovered);
		Render2D.gradientH(ctx, x, y, w, h, 6, ColorUtil.brighten(Theme.accent(), 1f + 0.15f * hv), ColorUtil.brighten(Theme.accentLight(), 1f + 0.1f * hv));
		Render2D.centeredText(ctx, label, x + w / 2, y + (h - 8) / 2 + 0.5f, 0xFFFFFFFF);
	}

	private static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		float x = panelX(), y = panelY();
		if (button == 0 && inside(mx, my, x + 206, y + 43, 80, 22)) {
			addHere();
			return true;
		}
		float lx = x + 14, ly = listY(), lw = W - 28;
		List<Waypoint> list = manager.current();
		for (int i = 0; i < VISIBLE_ROWS && i + scroll < list.size(); i++) {
			float ry = ly + i * ROW_H;
			if (!inside(mx, my, lx, ry, lw, ROW_H - 3)) continue;
			Waypoint wp = list.get(i + scroll);
			if (button == 0 && mx >= lx + lw - 20) {
				manager.remove(wp);
			} else if (button == 0) {
				wp.visible = !wp.visible;
				manager.save();
			} else if (button == 1) {
				int index = 0;
				for (int c = 0; c < WaypointManager.PALETTE.length; c++) if (WaypointManager.PALETTE[c] == wp.color) index = c;
				wp.color = WaypointManager.PALETTE[(index + 1) % WaypointManager.PALETTE.length];
				manager.save();
			}
			return true;
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
			addHere();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void addHere() {
		if (client == null || client.player == null) return;
		String name = nameField.getText().trim();
		if (name.isEmpty()) name = I18n.translate("zerovisual.waypoints.default_name", manager.current().size() + 1);
		manager.add(name, client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());
		nameField.setText("");
		scroll = Math.max(0, manager.current().size() - VISIBLE_ROWS);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (client != null) client.setScreen(parent);
	}
}
