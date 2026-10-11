package com.zerovisual.module;

public enum Category {
	VISUALS("Visuals", "◉"),
	COSMETICS("Cosmetics", "✦"),
	HUD("HUD", "▣"),
	UTILITIES("Utilities", "⚙");

	private final String displayName;
	private final String icon;

	Category(String displayName, String icon) {
		this.displayName = displayName;
		this.icon = icon;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getIcon() {
		return icon;
	}
}
