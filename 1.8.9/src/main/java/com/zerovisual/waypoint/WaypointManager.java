package com.zerovisual.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zerovisual.ZeroVisualClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Waypoints per world (singleplayer save or server address), stored in config/zerovisual/waypoints.json. */
public class WaypointManager {
	public static final int[] PALETTE = {0xFF8B5CF6, 0xFF3B82F6, 0xFF22D3EE, 0xFF34D399, 0xFFFACC15, 0xFFFB923C, 0xFFF43F5E, 0xFFF0F0F5};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file = new java.io.File(Minecraft.getMinecraft().mcDataDir, "config").toPath().resolve("zerovisual").resolve("waypoints.json");
	private final Map<String, List<Waypoint>> byWorld = new HashMap<>();

	public void load() {
		if (!Files.exists(file)) return;
		try {
			JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> world : root.entrySet()) {
				List<Waypoint> list = new ArrayList<>();
				for (JsonElement e : world.getValue().getAsJsonArray()) {
					JsonObject o = e.getAsJsonObject();
					list.add(new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
						o.get("dimension").getAsString(), o.get("color").getAsInt(), !o.has("visible") || o.get("visible").getAsBoolean()));
				}
				byWorld.put(world.getKey(), list);
			}
		} catch (Exception e) {
			ZeroVisualClient.LOGGER.error("Failed to load waypoints", e);
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		byWorld.forEach((world, list) -> {
			JsonArray array = new JsonArray();
			for (Waypoint w : list) {
				JsonObject o = new JsonObject();
				o.addProperty("name", w.name);
				o.addProperty("x", w.x);
				o.addProperty("y", w.y);
				o.addProperty("z", w.z);
				o.addProperty("dimension", w.dimension);
				o.addProperty("color", w.color);
				o.addProperty("visible", w.visible);
				array.add(o);
			}
			root.add(world, array);
		});
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			ZeroVisualClient.LOGGER.error("Failed to save waypoints", e);
		}
	}

	/** Key of the current world: the singleplayer save name or the server address. */
	public static String worldKey() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.getIntegratedServer() != null) return "sp:" + mc.getIntegratedServer().getWorldName();
		ServerData info = mc.getCurrentServerData();
		return info != null ? "mp:" + info.serverIP : "unknown";
	}

	public static String currentDimension() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null) return "";
		// the names of the newer versions, so the files look the same
		int id = mc.theWorld.provider.getDimensionId();
		return id == -1 ? "minecraft:the_nether" : id == 1 ? "minecraft:the_end" : id == 0 ? "minecraft:overworld" : "dimension:" + id;
	}

	/** Waypoints of the current world (all dimensions). The list is live: changes need save(). */
	public List<Waypoint> current() {
		return byWorld.computeIfAbsent(worldKey(), k -> new ArrayList<>());
	}

	public Waypoint add(String name, int x, int y, int z) {
		List<Waypoint> list = current();
		Waypoint waypoint = new Waypoint(name, x, y, z, currentDimension(), PALETTE[list.size() % PALETTE.length], true);
		list.add(waypoint);
		save();
		return waypoint;
	}

	public void remove(Waypoint waypoint) {
		current().remove(waypoint);
		save();
	}

	public static final class Waypoint {
		public String name;
		public final int x, y, z;
		public final String dimension;
		public int color;
		public boolean visible;

		public Waypoint(String name, int x, int y, int z, String dimension, int color, boolean visible) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
			this.dimension = dimension;
			this.color = color;
			this.visible = visible;
		}
	}
}
