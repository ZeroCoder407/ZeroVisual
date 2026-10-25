package com.zerovisual.gui;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import com.google.gson.JsonObject;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.module.impl.hud.Notifications;
import com.zerovisual.module.impl.utilities.ClickGui;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public class ClickGuiScreen extends Screen {
	private static final float GAP = 10;
	private static final float SEARCH_W = 170;
	private static final float SEARCH_H = 20;

	/** Shared between screen instances so panel positions survive reopening. */
	private static final Map<Category, Panel.PanelState> LAYOUT = new EnumMap<>(Category.class);

	@Nullable
	private final Screen parent;
	private final List<Panel> panels = new ArrayList<>();
	private final Anim openAnim = new Anim(10f, 0f);
	private final Anim searchFocus = new Anim(14f, 0f);
	private final Anim profilesHover = new Anim(18f, 0f);

	private Panel dragging;
	private float dragOffsetX, dragOffsetY;
	private String search = "";
	private boolean searchFocused;
	private float scale = 1f;

	public ClickGuiScreen(@Nullable Screen parent) {
		super(Component.literal(ZeroVisualClient.NAME));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panels.clear();
		Category[] categories = Category.values();
		// as many panels per row as fit, the rest go to a second row
		int perRow = Math.max(1, Math.min(categories.length, (int) ((width - 12 + GAP) / (Panel.WIDTH + GAP))));
		float total = perRow * Panel.WIDTH + (perRow - 1) * GAP;
		float startX = (width - total) / 2f;
		float rowStep = Math.max(120, (height - 60) / 2f);
		// After a resize (or with an old layout) a panel may be off screen: then lay all panels out again,
		// clamping them one by one would stack them on top of each other.
		boolean offScreen = false;
		for (Category category : categories) {
			Panel.PanelState s = LAYOUT.get(category);
			// a new category would land on top of the saved panels
			if (s == null && !LAYOUT.isEmpty()) offScreen = true;
			if (s != null && (s.x < 0 || s.x > width - Panel.WIDTH || s.y < 0 || s.y > height - Panel.HEADER - 40)) offScreen = true;
		}
		if (offScreen) LAYOUT.clear();

		for (int i = 0; i < categories.length; i++) {
			Category category = categories[i];
			float defaultX = startX + (i % perRow) * (Panel.WIDTH + GAP);
			float defaultY = 36 + (i / perRow) * rowStep;
			Panel.PanelState state = LAYOUT.computeIfAbsent(category, c -> new Panel.PanelState(defaultX, defaultY));
			List<Module> modules = ZeroVisualClient.get().modules().getByCategory(category);
			panels.add(new Panel(category, modules, state));
		}
	}

	// ---- Rendering ----

	@Override
	public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) extractPanorama(ctx, delta);
		if (ZeroVisualClient.get().modules().get(ClickGui.class).blur.get()) extractBlurredBackground(ctx);
		float o = openAnim.get();
		ctx.fillGradient(0, 0, width, height, ColorUtil.withAlpha(0x000000, 0.35f * o), ColorUtil.withAlpha(0x05030A, 0.65f * o));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);

		float o = openAnim.update(1f);
		float eased = 1f - (1f - o) * (1f - o) * (1f - o);
		scale = 0.92f + 0.08f * eased;
		double mx = toLocalX(mouseX);
		double my = toLocalY(mouseY);

		ctx.pose().pushMatrix();
		ctx.pose().translate(width / 2f, height / 2f);
		ctx.pose().scale(scale, scale);
		ctx.pose().translate(-width / 2f, -height / 2f);
		Render2D.setAlpha(eased);

		for (Panel panel : panels) panel.render(ctx, mx, my, search, height);
		renderSearch(ctx, mx, my);
		renderDescription(ctx, mx, my);

		Render2D.setAlpha(1f);
		ctx.pose().popMatrix();

		Notifications notifications = ZeroVisualClient.get().modules().get(Notifications.class);
		if (notifications.isEnabled()) notifications.renderOnTop(ctx);
	}

	private float searchX() {
		// search bar and the profiles button are centered together
		return (width - SEARCH_W - 80) / 2f;
	}

	private float searchY() {
		return height - SEARCH_H - 14;
	}

	private void renderSearch(GuiGraphicsExtractor ctx, double mx, double my) {
		float x = searchX(), y = searchY();
		float f = searchFocus.update(searchFocused);
		int accent = Theme.accent();

		Render2D.shadow(ctx, x, y + 2, SEARCH_W, SEARCH_H, 8, 10, 0x80000000);
		Render2D.rect(ctx, x, y, SEARCH_W, SEARCH_H, 8, Theme.BACKGROUND);
		Render2D.outline(ctx, x, y, SEARCH_W, SEARCH_H, 8, 1, ColorUtil.lerp(0x14FFFFFF, accent, f));

		// magnifier
		int iconColor = ColorUtil.lerp(Theme.TEXT_DIM, Theme.accentLight(), f);
		Render2D.outline(ctx, x + 8, y + 5.5f, 7, 7, 3.5f, 1.2f, iconColor);
		Render2D.rect(ctx, x + 14, y + 12, 3, 1.4f, 0.7f, iconColor);

		boolean empty = search.isEmpty();
		String shown = empty && !searchFocused ? I18n.get("zerovisual.gui.search") : search;
		if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) shown += "_";
		Render2D.text(ctx, shown, x + 22, y + 6, empty ? Theme.TEXT_DIM : Theme.TEXT);

		// profiles button to the right of the search bar
		float bx = profilesX(), bw = 74;
		float hv = profilesHover.update(mx >= bx && mx <= bx + bw && my >= y && my <= y + SEARCH_H);
		Render2D.shadow(ctx, bx, y + 2, bw, SEARCH_H, 8, 10, 0x80000000);
		Render2D.rect(ctx, bx, y, bw, SEARCH_H, 8, ColorUtil.lerp(Theme.BACKGROUND, 0xF0181822, hv));
		Render2D.outline(ctx, bx, y, bw, SEARCH_H, 8, 1, ColorUtil.lerp(0x14FFFFFF, accent, hv));
		Render2D.centeredText(ctx, "☰ " + I18n.get("zerovisual.profiles.title"), bx + bw / 2, y + 6, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv));
	}

	private float profilesX() {
		return searchX() + SEARCH_W + 6;
	}

	private void renderDescription(GuiGraphicsExtractor ctx, double mx, double my) {
		Module hovered = null;
		for (int i = panels.size() - 1; i >= 0 && hovered == null; i--) {
			Panel panel = panels.get(i);
			if (panel.isHovered(mx, my)) hovered = panel.getHoveredModule(mx, my, search);
			if (panel.isHovered(mx, my)) break;
		}
		if (hovered == null) return;
		Render2D.centeredText(ctx, hovered.getDescription(), width / 2f, searchY() - 14, Theme.TEXT_MUTED);
	}

	// ---- Input ----

	private double toLocalX(double x) {
		return width / 2.0 + (x - width / 2.0) / scale;
	}

	private double toLocalY(double y) {
		return height / 2.0 + (y - height / 2.0) / scale;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
		int button = event.button();
		double mx = toLocalX(mouseX), my = toLocalY(mouseY);

		// a key field waiting for input takes mouse buttons too (e.g. side buttons for zoom)
		for (Panel panel : panels) {
			if (panel.isListening() && panel.captureMouse(button)) return true;
		}

		if (button == InputConstants.MOUSE_BUTTON_LEFT && mx >= profilesX() && mx <= profilesX() + 74 && my >= searchY() && my <= searchY() + SEARCH_H) {
			ZeroVisualClient.get().config().save();
			minecraft.gui.setScreen(new ProfilesScreen(this));
			return true;
		}

		searchFocused = mx >= searchX() && mx <= searchX() + SEARCH_W && my >= searchY() && my <= searchY() + SEARCH_H;
		if (searchFocused) {
			if (button == InputConstants.MOUSE_BUTTON_RIGHT) search = "";
			return true;
		}

		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel panel = panels.get(i);
			if (!panel.isHovered(mx, my)) continue;
			// bring to front
			panels.remove(i);
			panels.add(panel);
			if (button == InputConstants.MOUSE_BUTTON_LEFT && panel.isHeaderHovered(mx, my)) {
				dragging = panel;
				dragOffsetX = (float) (mx - panel.state.x);
				dragOffsetY = (float) (my - panel.state.y);
				return true;
			}
			return panel.mouseClicked(mx, my, button, search);
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		double mouseX = event.x(), mouseY = event.y();
		int button = event.button();
		dragging = null;
		double mx = toLocalX(mouseX), my = toLocalY(mouseY);
		for (Panel panel : panels) panel.mouseReleased(mx, my, button);
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		double mouseX = event.x(), mouseY = event.y();
		int button = event.button();
		if (dragging != null) {
			dragging.state.x = (float) (toLocalX(mouseX) - dragOffsetX);
			dragging.state.y = (float) (toLocalY(mouseY) - dragOffsetY);
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double mx = toLocalX(mouseX), my = toLocalY(mouseY);
		for (int i = panels.size() - 1; i >= 0; i--) {
			if (panels.get(i).isHovered(mx, my)) {
				panels.get(i).scroll(verticalAmount);
				break;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key(), scanCode = event.keycode(), modifiers = event.modifiers();
		for (Panel panel : panels) {
			if (panel.isListening()) {
				panel.keyPressed(keyCode);
				return true;
			}
		}

		if (keyCode == InputConstants.KEY_F && (modifiers & InputConstants.MOD_CONTROL) != 0) {
			searchFocused = true;
			return true;
		}

		if (searchFocused) {
			if (keyCode == InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
				search = (modifiers & InputConstants.MOD_CONTROL) != 0 ? "" : search.substring(0, search.length() - 1);
				return true;
			}
			if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_RETURN) {
				searchFocused = false;
				return true;
			}
			return true;
		}

		if (keyCode == ZeroVisualClient.get().modules().get(ClickGui.class).getKey()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		char chr = (char) event.codepoint();
		int modifiers = 0;
		if (Character.isISOControl(chr)) return false;
		if (!searchFocused) {
			if (!Character.isLetterOrDigit(chr)) return false;
			searchFocused = true;
		}
		if (search.length() < 24) search += chr;
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		ZeroVisualClient.get().config().save();
		minecraft.gui.setScreen(parent);
	}

	// ---- Layout persistence ----

	public static JsonObject saveLayout() {
		JsonObject json = new JsonObject();
		LAYOUT.forEach((category, state) -> {
			JsonObject s = new JsonObject();
			s.addProperty("x", state.x);
			s.addProperty("y", state.y);
			s.addProperty("open", state.open);
			json.add(category.name(), s);
		});
		return json;
	}

	public static void loadLayout(JsonObject json) {
		for (Category category : Category.values()) {
			if (!json.has(category.name())) continue;
			JsonObject s = json.getAsJsonObject(category.name());
			Panel.PanelState state = new Panel.PanelState(s.get("x").getAsFloat(), s.get("y").getAsFloat());
			state.open = !s.has("open") || s.get("open").getAsBoolean();
			LAYOUT.put(category, state);
		}
	}
}
