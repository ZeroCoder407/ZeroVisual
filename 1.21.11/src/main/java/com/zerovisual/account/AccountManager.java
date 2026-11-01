package com.zerovisual.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerovisual.ZeroVisualClient;
import com.zerovisual.mixin.MinecraftClientAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.core.UUIDUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Offline (nickname) accounts for offline-mode servers and singleplayer.
 * Only nicknames are stored; no passwords or tokens.
 */
public class AccountManager {
	public static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String[] NAME_PARTS_A = {"Zero", "Night", "Star", "Void", "Frost", "Neon", "Pixel", "Shadow", "Lunar", "Echo"};
	private static final String[] NAME_PARTS_B = {"Fox", "Wolf", "Rider", "Blade", "Ghost", "Hawk", "Byte", "Drift", "Storm", "Nova"};

	private final Path file = FabricLoader.getInstance().getConfigDir().resolve("zerovisual-accounts.json");
	private final List<String> accounts = new ArrayList<>();
	private final Random random = new Random();

	/** The session the game was launched with, so it can be restored. */
	private User originalSession;
	private ProfileKeyPairManager originalProfileKeys;

	public void load() {
		if (!Files.exists(file)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("offline")) {
				String name = e.getAsString();
				if (VALID_NAME.matcher(name).matches() && !accounts.contains(name)) accounts.add(name);
			}
		} catch (Exception e) {
			ZeroVisualClient.LOGGER.error("Failed to load accounts", e);
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		JsonArray list = new JsonArray();
		accounts.forEach(list::add);
		root.add("offline", list);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ZeroVisualClient.LOGGER.error("Failed to save accounts", e);
		}
	}

	public List<String> getAccounts() {
		return Collections.unmodifiableList(accounts);
	}

	/** @return false if the name is invalid or already added */
	public boolean add(String name) {
		if (!VALID_NAME.matcher(name).matches()) return false;
		for (String existing : accounts) {
			if (existing.equalsIgnoreCase(name)) return false;
		}
		accounts.add(name);
		save();
		return true;
	}

	public void remove(String name) {
		if (accounts.remove(name)) save();
	}

	public String randomName() {
		String name;
		do {
			name = NAME_PARTS_A[random.nextInt(NAME_PARTS_A.length)]
				+ NAME_PARTS_B[random.nextInt(NAME_PARTS_B.length)]
				+ (random.nextInt(900) + 100);
		} while (name.length() > 16);
		return name;
	}

	public String currentName() {
		return Minecraft.getInstance().getUser().getName();
	}

	public boolean isOriginal() {
		return originalSession == null || Minecraft.getInstance().getUser() == originalSession;
	}

	public String originalName() {
		return originalSession != null ? originalSession.getName() : currentName();
	}

	public void login(String name) {
		Minecraft mc = Minecraft.getInstance();
		rememberOriginal(mc);
		User session = new User(name, UUIDUtil.createOfflinePlayerUUID(name), "0",
			Optional.empty(), Optional.empty());
		MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
		accessor.zerovisual$setSession(session);
		// Offline sessions cannot sign chat; keys of the previous account must not be reused.
		accessor.zerovisual$setProfileKeys(ProfileKeyPairManager.EMPTY_KEY_MANAGER);
		ZeroVisualClient.LOGGER.info("Switched to offline account {}", name);
	}

	public void restoreOriginal() {
		if (originalSession == null) return;
		MinecraftClientAccessor accessor = (MinecraftClientAccessor) Minecraft.getInstance();
		accessor.zerovisual$setSession(originalSession);
		accessor.zerovisual$setProfileKeys(originalProfileKeys);
		ZeroVisualClient.LOGGER.info("Restored original account {}", originalSession.getName());
	}

	private void rememberOriginal(Minecraft mc) {
		if (originalSession != null) return;
		originalSession = mc.getUser();
		originalProfileKeys = mc.getProfileKeyPairManager();
	}
}
