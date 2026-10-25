package com.zerovisual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * A hold key (keyboard or mouse button) that is a view of a vanilla KeyBinding:
 * changing it here also changes it in Options → Controls and the other way round.
 * The binding is saved by Minecraft in options.txt, so nothing is stored in our config.
 */
public class KeySetting extends Setting<String> {
	private final Supplier<KeyMapping> binding;

	public KeySetting(String name, Supplier<KeyMapping> binding) {
		super(name, "");
		this.binding = binding;
	}

	@Override
	public String get() {
		KeyMapping b = binding.get();
		return b != null ? b.saveString() : InputConstants.UNKNOWN.getName();
	}

	@Override
	public void set(String translationKey) {
		KeyMapping b = binding.get();
		if (b == null) return;
		b.setKey(InputConstants.getKey(translationKey));
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	public void setKey(InputConstants.Key key) {
		set(key.getName());
	}

	@Override
	public void reset() {
		KeyMapping b = binding.get();
		if (b != null) setKey(b.getDefaultKey());
	}

	public String displayName() {
		InputConstants.Key key = InputConstants.getKey(get());
		return key.equals(InputConstants.UNKNOWN) ? "None" : key.getDisplayName().getString();
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
