package com.zerovisual.module.impl.utilities;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/** "Reconnect" button on the disconnect screen, optionally with an automatic countdown. */
public class AutoReconnect extends Module {
	public final BooleanSetting auto = add(new BooleanSetting("Auto", true));
	public final NumberSetting delay = add(new NumberSetting("Delay", 5, 1, 30, 1)
		.visibleWhen(auto::get));

	private ServerInfo lastServer;

	public AutoReconnect() {
		super("AutoReconnect", "Reconnect button and timer on disconnect", Category.UTILITIES);
		setEnabled(true);
	}

	/** Remembers the server on join (called from the play connection event). */
	public void onJoin() {
		ServerInfo info = mc.getCurrentServerEntry();
		if (info != null) lastServer = info;
	}

	public void onDisconnectedScreen(Screen screen) {
		if (!isEnabled() || lastServer == null) return;
		ServerInfo server = lastServer;

		int bottom = screen.height / 2;
		for (ClickableWidget widget : Screens.getButtons(screen)) bottom = Math.max(bottom, widget.getY() + widget.getHeight());
		int y = bottom + 4;

		long[] deadline = {auto.get() ? System.currentTimeMillis() + Math.round(delay.get() * 1000) : 0};
		ButtonWidget reconnect = ButtonWidget.builder(label(deadline[0]), b -> reconnect(server))
			.dimensions(screen.width / 2 - 100, y, 200, 20).build();
		Screens.getButtons(screen).add(reconnect);

		if (deadline[0] > 0) {
			ButtonWidget cancel = ButtonWidget.builder(Text.translatable("zerovisual.reconnect.cancel"), b -> {
				deadline[0] = 0;
				reconnect.setMessage(label(0));
				b.visible = false;
			}).dimensions(screen.width / 2 - 100, y + 24, 200, 20).build();
			Screens.getButtons(screen).add(cancel);

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

	private static Text label(long deadline) {
		if (deadline == 0) return Text.translatable("zerovisual.reconnect");
		long seconds = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
		return Text.translatable("zerovisual.reconnect.timer", seconds);
	}

	private void reconnect(ServerInfo server) {
		ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), mc, ServerAddress.parse(server.address), server, false, null);
	}

	public static boolean isDisconnectScreen(Screen screen) {
		return screen instanceof DisconnectedScreen;
	}
}
