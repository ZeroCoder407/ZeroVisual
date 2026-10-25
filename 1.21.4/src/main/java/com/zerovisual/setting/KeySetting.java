package com.zerovisual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.function.Supplier;

/**
 * A hold key (keyboard or mouse button) that is a view of a vanilla KeyBinding:
 * changing it here also changes it in Options → Controls and the other way round.
 * The binding is saved by Minecraft in options.txt, so nothing is stored in our config.
 */
public class KeySetting extends Setting<String> {
	private final Supplier<KeyBinding> binding;

	public KeySetting(String name, Supplier<KeyBinding> binding) {
		super(name, "");
		this.binding = binding;
	}

	@Override
	public String get() {
		KeyBinding b = binding.get();
		return b != null ? b.getBoundKeyTranslationKey() : InputUtil.UNKNOWN_KEY.getTranslationKey();
	}

	@Override
	public void set(String translationKey) {
		KeyBinding b = binding.get();
		if (b == null) return;
		b.setBoundKey(InputUtil.fromTranslationKey(translationKey));
		KeyBinding.updateKeysByCode();
		MinecraftClient.getInstance().options.write();
	}

	public void setKey(InputUtil.Key key) {
		set(key.getTranslationKey());
	}

	@Override
	public void reset() {
		KeyBinding b = binding.get();
		if (b != null) setKey(b.getDefaultKey());
	}

	public String displayName() {
		InputUtil.Key key = InputUtil.fromTranslationKey(get());
		return key.equals(InputUtil.UNKNOWN_KEY) ? "None" : key.getLocalizedText().getString();
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
