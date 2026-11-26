package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Anim;
import com.zerovisual.render.ColorUtil;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.util.CpsCounter;
import com.zerovisual.render.DrawContext;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.settings.KeyBinding;

/** WASD, mouse buttons with CPS and the jump key, lit while pressed. */
public class Keystrokes extends HudModule {
	private static final float KEY = 22;
	private static final float GAP = 2;

	public final BooleanSetting showMouse = add(new BooleanSetting("Mouse buttons", true));
	public final BooleanSetting showSpace = add(new BooleanSetting("Space", true));

	private final Anim[] anims = new Anim[7];

	public Keystrokes() {
		super("Keystrokes", "WASD, mouse (with CPS) and space presses");
		for (int i = 0; i < anims.length; i++) anims[i] = new Anim(20f, 0f);
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		return new float[]{screenW - w - 6, screenH - h - 6};
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		float totalW = KEY * 3 + GAP * 2;
		float totalH = KEY * 2 + GAP;
		if (showMouse.get()) totalH += GAP + KEY;
		if (showSpace.get()) totalH += GAP + 12;

		float[] pos = place(ctx, totalW, totalH);
		float x = pos[0];
		float y = pos[1];

		key(ctx, 0, mc.gameSettings.keyBindForward, x + KEY + GAP, y, KEY, KEY, null);
		float row2 = y + KEY + GAP;
		key(ctx, 1, mc.gameSettings.keyBindLeft, x, row2, KEY, KEY, null);
		key(ctx, 2, mc.gameSettings.keyBindBack, x + KEY + GAP, row2, KEY, KEY, null);
		key(ctx, 3, mc.gameSettings.keyBindRight, x + (KEY + GAP) * 2, row2, KEY, KEY, null);
		float next = row2 + KEY + GAP;

		if (showMouse.get()) {
			float mw = (totalW - GAP) / 2;
			key(ctx, 4, mc.gameSettings.keyBindAttack, x, next, mw, KEY, "LMB|" + CpsCounter.left());
			key(ctx, 5, mc.gameSettings.keyBindUseItem, x + mw + GAP, next, mw, KEY, "RMB|" + CpsCounter.right());
			next += KEY + GAP;
		}
		if (showSpace.get()) {
			key(ctx, 6, mc.gameSettings.keyBindJump, x, next, totalW, 12, "");
		}
	}

	private void key(DrawContext ctx, int index, KeyBinding binding, float x, float y, float w, float h, String label) {
		float p = anims[index].update(binding.isKeyDown());
		int accent = Theme.accent();

		Render2D.shadow(ctx, x, y + 1, w, h, 5, 4, 0x50000000);
		Render2D.rect(ctx, x, y, w, h, 5, ColorUtil.lerp(Theme.cardColor(), ColorUtil.withAlpha(accent, 0.85f), p));
		Theme.cardEdge(ctx, x, y, w, h, 5);
		int textColor = ColorUtil.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, p);

		if (label == null) {
			// letters by key code, so a non-latin keyboard layout still shows W A S D
			// LWJGL key names do not depend on the keyboard layout, so W A S D stay latin
			int code = binding.getKeyCode();
			String name = code < 0 ? "M" + (code + 101) : String.valueOf(Keyboard.getKeyName(code)).toUpperCase();
			if (name.length() > 3) name = name.substring(0, 3);
			Render2D.centeredText(ctx, name, x + w / 2 + 0.5f, y + (h - 8) / 2 + 0.5f, textColor);
		} else if (label.isEmpty()) {
			Render2D.rect(ctx, x + w / 2 - 12, y + h / 2 - 0.75f, 24, 1.5f, 0.75f, textColor);
		} else {
			String[] parts = label.split("\\|");
			Render2D.centeredText(ctx, parts[0], x + w / 2 + 0.5f, y + 3, textColor);
			Render2D.scaledText(ctx, parts[1] + " CPS", x + w / 2 - Render2D.textWidth(parts[1] + " CPS") * 0.65f / 2, y + 13, 0.65f,
				ColorUtil.lerp(Theme.TEXT_DIM, 0xFFE0E0FF, p));
		}
	}
}
