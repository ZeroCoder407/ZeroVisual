package com.zerovisual.account;

import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerovisual.ZeroVisualClient;

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

	private final Path file = new java.io.File(Minecraft.getMinecraft().mcDataDir, "config").toPath().resolve("zerovisual-accounts.json");
	private final List<String> accounts = new ArrayList<>();
	private final Random random = new Random();

	/** The session the game was launched with, so it can be restored. */
	private Session originalSession;

	public void load() {
		if (!Files.exists(file)) return;
		try {
			JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
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
		for (String name : accounts) list.add(new com.google.gson.JsonPrimitive(name));
		root.add("offline", list);
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
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
		return Minecraft.getMinecraft().getSession().getUsername();
	}

	public boolean isOriginal() {
		return originalSession == null || Minecraft.getMinecraft().getSession() == originalSession;
	}

	public String originalName() {
		return originalSession != null ? originalSession.getUsername() : currentName();
	}

	public void login(String name) {
		Minecraft mc = Minecraft.getMinecraft();
		rememberOriginal(mc);
		String uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
		setSession(mc, new Session(name, uuid, "0", "legacy"));
		ZeroVisualClient.LOGGER.info("Switched to offline account " + name);
	}

	public void restoreOriginal() {
		if (originalSession == null) return;
		setSession(Minecraft.getMinecraft(), originalSession);
		ZeroVisualClient.LOGGER.info("Restored original account " + originalSession.getUsername());
	}

	private void rememberOriginal(Minecraft mc) {
		if (originalSession != null) return;
		originalSession = mc.getSession();
	}

	/** Minecraft#session is final: set through reflection, by its dev name or its Forge (srg) name in a normal game. */
	private static void setSession(Minecraft mc, Session session) {
		ReflectionHelper.setPrivateValue(Minecraft.class, mc, session, "session", "field_71449_j");
	}
}
