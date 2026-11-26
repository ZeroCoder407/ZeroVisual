package com.zerovisual.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.gui.ClickGuiScreen;
import com.zerovisual.module.HudModule;
import com.zerovisual.module.Module;
import com.zerovisual.module.ModuleManager;
import com.zerovisual.setting.Setting;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Saves modules, settings, binds and GUI layout to config/zerovisual.json. */
public class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file = new java.io.File(Minecraft.getMinecraft().mcDataDir, "config").toPath().resolve("zerovisual.json");
	private final ModuleManager modules;

	public ConfigManager(ModuleManager modules) {
		this.modules = modules;
	}

	public void load() {
		if (!Files.exists(file)) return;
		try {
			apply(new JsonParser().parse(read(file)).getAsJsonObject());
		} catch (Exception e) {
			ZeroVisualClient.LOGGER.error("Failed to load config, using defaults", e);
		}
	}

	// ---- Profiles: named copies of the whole config in config/zerovisual/profiles ----

	private Path profilesDir() {
		return file.resolveSibling("zerovisual").resolve("profiles");
	}

	public static boolean isValidProfileName(String name) {
		return name.matches("[\\p{L}0-9 _-]{1,24}");
	}

	public List<String> listProfiles() {
		List<String> names = new ArrayList<String>();
		if (!Files.isDirectory(profilesDir())) return names;
		try (Stream<Path> files = Files.list(profilesDir())) {
			files.filter(p -> p.getFileName().toString().endsWith(".json"))
				.map(p -> p.getFileName().toString().replaceFirst("\\.json$", ""))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.forEach(names::add);
		} catch (IOException e) {
			ZeroVisualClient.LOGGER.error("Failed to list profiles", e);
		}
		return names;
	}

	public boolean saveProfile(String name) {
		if (!isValidProfileName(name)) return false;
		try {
			Files.createDirectories(profilesDir());
			write(profilesDir().resolve(name + ".json"), GSON.toJson(serialize()));
			return true;
		} catch (IOException e) {
			ZeroVisualClient.LOGGER.error("Failed to save profile " + name, e);
			return false;
		}
	}

	public boolean loadProfile(String name) {
		Path path = profilesDir().resolve(name + ".json");
		if (!isValidProfileName(name) || !Files.exists(path)) return false;
		try {
			apply(new JsonParser().parse(read(path)).getAsJsonObject());
			save();
			return true;
		} catch (Exception e) {
			ZeroVisualClient.LOGGER.error("Failed to load profile " + name, e);
			return false;
		}
	}

	public void deleteProfile(String name) {
		if (!isValidProfileName(name)) return;
		try {
			Files.deleteIfExists(profilesDir().resolve(name + ".json"));
		} catch (IOException e) {
			ZeroVisualClient.LOGGER.error("Failed to delete profile " + name, e);
		}
	}

	private void apply(JsonObject root) {
		JsonObject moduleData = root.getAsJsonObject("modules");
		if (moduleData != null) {
			for (Module module : modules.getModules()) {
				JsonObject data = moduleData.getAsJsonObject(module.getName());
				if (data != null) loadModule(module, data);
			}
		}

		JsonObject gui = root.getAsJsonObject("gui");
		if (gui != null) ClickGuiScreen.loadLayout(gui);
	}

	private void loadModule(Module module, JsonObject data) {
		if (data.has("key")) module.setKey(data.get("key").getAsInt());

		JsonObject settings = data.getAsJsonObject("settings");
		if (settings != null) {
			for (Setting<?> setting : module.getSettings()) {
				JsonElement value = settings.get(setting.getName());
				if (value != null) setting.fromJson(value);
			}
		}

		if (module instanceof HudModule && data.has("hud")) {
			HudModule hud = (HudModule) module;
			JsonObject pos = data.getAsJsonObject("hud");
			hud.setHudPosition(pos.get("x").getAsFloat(), pos.get("y").getAsFloat());
		}

		if (data.has("enabled")) module.setEnabled(data.get("enabled").getAsBoolean());
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			write(tmp, GSON.toJson(serialize()));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			ZeroVisualClient.LOGGER.error("Failed to save config", e);
		}
	}

	private static String read(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static void write(Path path, String text) throws IOException {
		Files.write(path, text.getBytes(StandardCharsets.UTF_8));
	}

	private JsonObject serialize() {
		JsonObject root = new JsonObject();

		JsonObject moduleData = new JsonObject();
		for (Module module : modules.getModules()) {
			JsonObject data = new JsonObject();
			data.addProperty("enabled", module.isEnabled());
			data.addProperty("key", module.getKey());
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : module.getSettings()) {
				settings.add(setting.getName(), setting.toJson());
			}
			data.add("settings", settings);
			if (module instanceof HudModule && ((HudModule) module).hasCustomPosition()) {
				HudModule hud = (HudModule) module;
				JsonObject pos = new JsonObject();
				pos.addProperty("x", hud.getHudX());
				pos.addProperty("y", hud.getHudY());
				data.add("hud", pos);
			}
			moduleData.add(module.getName(), data);
		}
		root.add("modules", moduleData);
		root.add("gui", ClickGuiScreen.saveLayout());
		return root;
	}
}
