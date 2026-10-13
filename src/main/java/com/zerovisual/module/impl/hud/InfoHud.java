package com.zerovisual.module.impl.hud;

import com.zerovisual.gui.Theme;
import com.zerovisual.module.HudModule;
import com.zerovisual.render.Anim;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ping, movement speed and server address. */
public class InfoHud extends HudModule {
	public final BooleanSetting ping = add(new BooleanSetting("Ping", true));
	public final BooleanSetting speed = add(new BooleanSetting("Speed", true));
	public final BooleanSetting server = add(new BooleanSetting("Server", true));

	private final Anim smoothSpeed = new Anim(6f, 0f);
	private double lastX, lastZ;
	private float speedPerSecond;

	public InfoHud() {
		super("InfoHUD", "Ping, speed and server address");
	}

	@Override
	public boolean isDraggable() {
		return true;
	}

	@Override
	protected float[] defaultPosition(float screenW, float screenH, float w, float h) {
		// under the watermark; the bottom left corner is covered by the chat
		return new float[]{6, 30};
	}

	@Override
	public void onTick() {
		if (mc.player == null) return;
		double dx = mc.player.getX() - lastX, dz = mc.player.getZ() - lastZ;
		lastX = mc.player.getX();
		lastZ = mc.player.getZ();
		// ignore teleports
		double distance = Math.sqrt(dx * dx + dz * dz);
		speedPerSecond = distance > 10 ? 0 : (float) (distance * 20);
	}

	@Override
	public void render(DrawContext ctx, float tickDelta) {
		if (mc.player == null) return;
		List<String[]> rows = new ArrayList<>();
		if (ping.get()) {
			PlayerListEntry entry = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) : null;
			rows.add(new String[]{"Ping", entry != null ? entry.getLatency() + " ms" : "—"});
		}
		if (speed.get()) {
			rows.add(new String[]{"Speed", String.format(Locale.ROOT, "%.1f b/s", smoothSpeed.update(speedPerSecond))});
		}
		if (server.get()) {
			ServerInfo info = mc.getCurrentServerEntry();
			rows.add(new String[]{"Server", info != null ? info.address : I18n.translate("zerovisual.hud.info.singleplayer")});
		}
		if (rows.isEmpty()) return;

		float labelW = 0, valueW = 0;
		for (String[] row : rows) {
			labelW = Math.max(labelW, Render2D.textWidth(row[0]));
			valueW = Math.max(valueW, Render2D.textWidth(row[1]));
		}
		float rowH = 11;
		float w = 8 + labelW + 10 + valueW + 8;
		float h = 6 + rows.size() * rowH + 2;
		float[] pos = place(ctx, w, h);
		float x = pos[0], y = pos[1];

		Theme.card(ctx, x, y, w, h);
		float ry = y + 5;
		for (String[] row : rows) {
			Render2D.text(ctx, row[0], x + 8, ry, Theme.accent());
			Render2D.text(ctx, row[1], x + 8 + labelW + 10, ry, Theme.TEXT);
			ry += rowH;
		}
	}
}
