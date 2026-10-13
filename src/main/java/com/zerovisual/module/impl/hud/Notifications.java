package com.zerovisual.module.impl.hud;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.ClickGuiScreen;
import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.module.Module;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Sliding cards on the right edge when a module is turned on or off. */
public class Notifications extends HudModule {
	private static final float W = 140;
	private static final float H = 28;
	private static final int MAX = 5;

	public final NumberSetting duration = add(new NumberSetting("Duration", 2.5, 1, 6, 0.5));

	private final List<Toast> toasts = new ArrayList<>();
	private boolean drawingOnTop;

	public Notifications() {
		super("Notifications", "Notifications when modules are toggled");
		setEnabled(true);
	}

	public void onToggle(Module module) {
		if (!isEnabled() || module == this) return;
		boolean on = module.isEnabled();
		post(module, module.getName(), I18n.translate(on ? "zerovisual.notify.enabled" : "zerovisual.notify.disabled"), on);
	}

	/** A card with any text, e.g. "Screenshot copied". Thread-safe: may be called from background threads. */
	public void notify(String title, String subtitle, boolean success) {
		if (!isEnabled()) return;
		mc.execute(() -> post(null, title, subtitle, success));
	}

	private void post(Object source, String title, String subtitle, boolean success) {
		// a repeated toggle replaces the card of the same module
		if (source != null) toasts.removeIf(t -> t.source == source);
		toasts.add(new Toast(source, title, subtitle, success, System.currentTimeMillis()));
		while (toasts.size() > MAX) toasts.removeFirst();
	}

	/** Called by the ClickGUI so the cards are drawn above its blurred background. */
	public void renderOnTop(DrawContext ctx) {
		drawingOnTop = true;
		render(ctx, 0);
		drawingOnTop = false;
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		if (toasts.isEmpty()) return;
		if (mc.currentScreen instanceof ClickGuiScreen && !drawingOnTop) return;
		long now = System.currentTimeMillis();
		long life = Math.round(duration.get() * 1000);

		float sw = ctx.getScaledWindowWidth();
		float y = ctx.getScaledWindowHeight() / 2f - H;
		// stay below the module list when it sits on the right side
		ModuleList list = ZeroVisualClient.get().modules().get(ModuleList.class);
		if (list.isEnabled() && list.isOnScreen() && list.getLastX() + list.getLastW() > sw - W - 6) {
			y = Math.max(y, list.getLastY() + list.getLastH() + 6);
		}
		Iterator<Toast> it = toasts.iterator();
		while (it.hasNext()) {
			Toast toast = it.next();
			long age = now - toast.start;
			boolean leaving = age > life;
			float slide = toast.slide.update(leaving ? 0f : 1f);
			if (leaving && slide < 0.01f) {
				it.remove();
				continue;
			}

			float x = sw - 6 - W * slide;
			float prev = Render2D.getAlpha();
			Render2D.setAlpha(slide);

			Theme.card(ctx, x, y, W, H);
			int stateColor = toast.success ? 0xFF5CFF8A : 0xFFFF5C6C;
			Render2D.gradientV(ctx, x + 6, y + 6, 16, 16, 5, Theme.accentLight(), Theme.accent());
			Render2D.centeredText(ctx, toast.success ? "✔" : "✖", x + 14.5f, y + 10, 0xFFFFFFFF);
			Render2D.text(ctx, toast.title, x + 28, y + 6, Theme.TEXT);
			Render2D.text(ctx, toast.subtitle, x + 28, y + 16, stateColor);

			float progress = 1f - Math.min(1f, age / (float) life);
			Render2D.rect(ctx, x + 6, y + H - 3, (W - 12) * progress, 1.5f, 0.75f, ColorUtil.withAlpha(Theme.accent(), 0.8f));

			Render2D.setAlpha(prev);
			y += (H + 4) * slide;
		}
	}

	private static final class Toast {
		final Object source;
		final String title;
		final String subtitle;
		final boolean success;
		final long start;
		final Anim slide = new Anim(12f, 0f);

		Toast(Object source, String title, String subtitle, boolean success, long start) {
			this.source = source;
			this.title = title;
			this.subtitle = subtitle;
			this.success = success;
			this.start = start;
		}
	}
}
