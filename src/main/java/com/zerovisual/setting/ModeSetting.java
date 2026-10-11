package com.zerovisual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String name, String defaultValue, String... modes) {
		super(name, defaultValue);
		this.modes = List.of(modes);
	}

	public List<String> getModes() {
		return modes;
	}

	public boolean is(String mode) {
		return get().equalsIgnoreCase(mode);
	}

	public void cycle(boolean forward) {
		int index = modes.indexOf(get());
		int next = Math.floorMod(index + (forward ? 1 : -1), modes.size());
		set(modes.get(next));
	}

	@Override
	public void set(String value) {
		if (modes.contains(value)) {
			super.set(value);
		}
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive()) {
			set(json.getAsString());
		}
	}
}
