package com.zerovisual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

/**
 * A hold key (keyboard or mouse button) that is a view of a vanilla KeyBinding:
 * changing it here also changes it in Options → Controls and the other way round.
 * The binding is saved by Minecraft in options.txt, so nothing is stored in our config.
 * The value is the 1.8.9 key code: LWJGL keyboard codes, mouse buttons as button - 100.
 */
public class KeySetting extends Setting<Integer> {
	public interface Binding {
		KeyBinding get();
	}

	private final Binding binding;

	public KeySetting(String name, Binding binding) {
		super(name, 0);
		this.binding = binding;
	}

	@Override
	public Integer get() {
		KeyBinding b = binding.get();
		return b != null ? b.getKeyCode() : 0;
	}

	@Override
	public void set(Integer code) {
		KeyBinding b = binding.get();
		if (b == null) return;
		Minecraft.getMinecraft().gameSettings.setOptionKeyBinding(b, code);
		KeyBinding.resetKeyBindingArrayAndHash();
		Minecraft.getMinecraft().gameSettings.saveOptions();
	}

	public void setKey(int code) {
		set(code);
	}

	@Override
	public void reset() {
		KeyBinding b = binding.get();
		if (b != null) setKey(b.getKeyCodeDefault());
	}

	public String displayName() {
		int code = get();
		return code == 0 ? "None" : GameSettings.getKeyDisplayString(code);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		// options.txt is the source of truth for vanilla key bindings
	}
}
