package com.zerovisual.module.impl.utilities;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** "Reconnect" button on the disconnect screen, optionally with an automatic countdown. */
public class AutoReconnect extends Module {
	public final BooleanSetting auto = add(new BooleanSetting("Auto", true));
	public final NumberSetting delay = add(new NumberSetting("Delay", 5, 1, 30, 1)
		.visibleWhen(auto::get));

	private ServerData lastServer;

	public AutoReconnect() {
		super("AutoReconnect", "Reconnect button and timer on disconnect", Category.UTILITIES);
		setEnabled(true);
	}

	/** Remembers the server on join (called from the play connection event). */
	public void onJoin() {
		ServerData info = mc.getCurrentServer();
		if (info != null) lastServer = info;
	}

	public void onDisconnectedScreen(Screen screen) {
		if (!isEnabled() || lastServer == null) return;
		ServerData server = lastServer;

		int bottom = screen.height / 2;
		for (AbstractWidget widget : Screens.getWidgets(screen)) bottom = Math.max(bottom, widget.getY() + widget.getHeight());
		int y = bottom + 4;

		long[] deadline = {auto.get() ? System.currentTimeMillis() + Math.round(delay.get() * 1000) : 0};
		Button reconnect = Button.builder(label(deadline[0]), b -> reconnect(server))
			.bounds(screen.width / 2 - 100, y, 200, 20).build();
		Screens.getWidgets(screen).add(reconnect);

		if (deadline[0] > 0) {
			Button cancel = Button.builder(Component.translatable("zerovisual.reconnect.cancel"), b -> {
				deadline[0] = 0;
				reconnect.setMessage(label(0));
				b.visible = false;
			}).bounds(screen.width / 2 - 100, y + 24, 200, 20).build();
			Screens.getWidgets(screen).add(cancel);

			ScreenEvents.afterTick(screen).register(s -> {
				if (deadline[0] == 0) return;
				if (System.currentTimeMillis() >= deadline[0]) {
					deadline[0] = 0;
					reconnect(server);
				} else {
					reconnect.setMessage(label(deadline[0]));
				}
			});
		}
	}

	private static Component label(long deadline) {
		if (deadline == 0) return Component.translatable("zerovisual.reconnect");
		long seconds = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
		return Component.translatable("zerovisual.reconnect.timer", seconds);
	}

	private void reconnect(ServerData server) {
		ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(server.ip), server, false, null);
	}

	public static boolean isDisconnectScreen(Screen screen) {
		return screen instanceof DisconnectedScreen;
	}
}
