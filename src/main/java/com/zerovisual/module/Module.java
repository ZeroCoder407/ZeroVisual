package com.zerovisual.module;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.hud.Notifications;
import com.zerovisual.setting.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
	protected static final MinecraftClient mc = MinecraftClient.getInstance();

	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;
	private int key = GLFW.GLFW_KEY_UNKNOWN;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	/** User toggle (key or menu): also shows a notification. Config loading uses setEnabled instead. */
	public void toggle() {
		setEnabled(!enabled);
		ZeroVisualClient client = ZeroVisualClient.get();
		if (client != null) client.modules().get(Notifications.class).onToggle(this);
	}

	/** Modules that are actions rather than on/off features (e.g. opening the GUI). */
	public boolean isToggleable() {
		return true;
	}

	/** Called when the module's bind is pressed in game. */
	public void onKeyPressed() {
		toggle();
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled) onEnable();
		else onDisable();
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called every client tick while enabled. */
	public void onTick() {
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(settings);
	}

	public int getKey() {
		return key;
	}

	public void setKey(int key) {
		this.key = key;
	}
}
