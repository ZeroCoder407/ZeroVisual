package com.zerovisual.gui;

import com.google.gson.JsonObject;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.module.impl.hud.Notifications;
import com.zerovisual.module.impl.utilities.ClickGui;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
		super(Text.literal(ZeroVisualClient.NAME));
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
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (client.world == null) renderPanoramaBackground(ctx, delta);
		if (ZeroVisualClient.get().modules().get(ClickGui.class).blur.get()) applyBlur();
		float o = openAnim.get();
		ctx.fillGradient(0, 0, width, height, ColorUtil.withAlpha(0x000000, 0.35f * o), ColorUtil.withAlpha(0x05030A, 0.65f * o));
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);

		float o = openAnim.update(1f);
		float eased = 1f - (1f - o) * (1f - o) * (1f - o);
		scale = 0.92f + 0.08f * eased;
		double mx = toLocalX(mouseX);
		double my = toLocalY(mouseY);

		ctx.getMatrices().push();
		ctx.getMatrices().translate(width / 2f, height / 2f, 0);
		ctx.getMatrices().scale(scale, scale, 1);
		ctx.getMatrices().translate(-width / 2f, -height / 2f, 0);
		Render2D.setAlpha(eased);

		for (Panel panel : panels) panel.render(ctx, mx, my, search, height);
		renderSearch(ctx, mx, my);
		renderDescription(ctx, mx, my);

		Render2D.setAlpha(1f);
		ctx.getMatrices().pop();

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

	private void renderSearch(DrawContext ctx, double mx, double my) {
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
		String shown = empty && !searchFocused ? "Поиск модулей..." : search;
		if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) shown += "_";
		Render2D.text(ctx, shown, x + 22, y + 6, empty ? Theme.TEXT_DIM : Theme.TEXT);

		// profiles button to the right of the search bar
		float bx = profilesX(), bw = 74;
		float hv = profilesHover.update(mx >= bx && mx <= bx + bw && my >= y && my <= y + SEARCH_H);
		Render2D.shadow(ctx, bx, y + 2, bw, SEARCH_H, 8, 10, 0x80000000);
		Render2D.rect(ctx, bx, y, bw, SEARCH_H, 8, ColorUtil.lerp(Theme.BACKGROUND, 0xF0181822, hv));
		Render2D.outline(ctx, bx, y, bw, SEARCH_H, 8, 1, ColorUtil.lerp(0x14FFFFFF, accent, hv));
		Render2D.centeredText(ctx, "☰ Профили", bx + bw / 2, y + 6, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv));
	}

	private float profilesX() {
		return searchX() + SEARCH_W + 6;
	}

	private void renderDescription(DrawContext ctx, double mx, double my) {
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
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		double mx = toLocalX(mouseX), my = toLocalY(mouseY);

		// a key field waiting for input takes mouse buttons too (e.g. side buttons for zoom)
		for (Panel panel : panels) {
			if (panel.isListening() && panel.captureMouse(button)) return true;
		}

		if (button == 0 && mx >= profilesX() && mx <= profilesX() + 74 && my >= searchY() && my <= searchY() + SEARCH_H) {
			ZeroVisualClient.get().config().save();
			client.setScreen(new ProfilesScreen(this));
			return true;
		}

		searchFocused = mx >= searchX() && mx <= searchX() + SEARCH_W && my >= searchY() && my <= searchY() + SEARCH_H;
		if (searchFocused) {
			if (button == 1) search = "";
			return true;
		}

		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel panel = panels.get(i);
			if (!panel.isHovered(mx, my)) continue;
			// bring to front
			panels.remove(i);
			panels.add(panel);
			if (button == 0 && panel.isHeaderHovered(mx, my)) {
				dragging = panel;
				dragOffsetX = (float) (mx - panel.state.x);
				dragOffsetY = (float) (my - panel.state.y);
				return true;
			}
			return panel.mouseClicked(mx, my, button, search);
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = null;
		double mx = toLocalX(mouseX), my = toLocalY(mouseY);
		for (Panel panel : panels) panel.mouseReleased(mx, my, button);
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging != null) {
			dragging.state.x = (float) (toLocalX(mouseX) - dragOffsetX);
			dragging.state.y = (float) (toLocalY(mouseY) - dragOffsetY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		for (Panel panel : panels) {
			if (panel.isListening()) {
				panel.keyPressed(keyCode);
				return true;
			}
		}

		if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
			searchFocused = true;
			return true;
		}

		if (searchFocused) {
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
				search = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 ? "" : search.substring(0, search.length() - 1);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
				searchFocused = false;
				return true;
			}
			return true;
		}

		if (keyCode == ZeroVisualClient.get().modules().get(ClickGui.class).getKey()) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (Character.isISOControl(chr)) return false;
		if (!searchFocused) {
			if (!Character.isLetterOrDigit(chr)) return false;
			searchFocused = true;
		}
		if (search.length() < 24) search += chr;
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		ZeroVisualClient.get().config().save();
		client.setScreen(parent);
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
