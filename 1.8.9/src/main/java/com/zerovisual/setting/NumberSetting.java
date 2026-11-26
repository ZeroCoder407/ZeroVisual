package com.zerovisual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;

	public NumberSetting(String name, double defaultValue, double min, double max, double step) {
		super(name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	@Override
	public void set(Double value) {
		double clamped = Math.max(min, Math.min(max, value));
		double stepped = Math.round(clamped / step) * step;
		super.set(Math.max(min, Math.min(max, stepped)));
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public double getStep() {
		return step;
	}

	public float getFloat() {
		return get().floatValue();
	}

	public int getInt() {
		return (int) Math.round(get());
	}

	/** Position of the value on the slider, 0..1. */
	public double getProgress() {
		return (get() - min) / (max - min);
	}

	public void setProgress(double progress) {
		set(min + (max - min) * Math.max(0, Math.min(1, progress)));
	}

	public String format() {
		if (step >= 1) {
			return String.valueOf(getInt());
		}
		int decimals = Math.max(0, (int) Math.ceil(-Math.log10(step)));
		return String.format(java.util.Locale.ROOT, "%." + decimals + "f", get());
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		}
	}
}
