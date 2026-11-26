package com.zerovisual.module.impl.utilities;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import com.zerovisual.util.Lang;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/** "Reconnect" button on the disconnect screen, optionally with an automatic countdown. */
public class AutoReconnect extends Module {
	private static final int RECONNECT_ID = 0x7A01, CANCEL_ID = 0x7A02;

	public final BooleanSetting auto = add(new BooleanSetting("Auto", true));
	public final NumberSetting delay = add(new NumberSetting("Delay", 5, 1, 30, 1)
		.visibleWhen(() -> auto.get()));

	private ServerData lastServer;
	private GuiButton reconnectButton;
	private long deadline;

	public AutoReconnect() {
		super("AutoReconnect", "Reconnect button and timer on disconnect", Category.UTILITIES);
		setEnabled(true);
	}

	/** Remembers the server while you play on it. */
	@Override
	public void onTick() {
		ServerData info = mc.getCurrentServerData();
		if (info != null && mc.theWorld != null) lastServer = info;
	}

	@SubscribeEvent
	public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
		reconnectButton = null;
		deadline = 0;
		if (!isEnabled() || lastServer == null || !(event.gui instanceof GuiDisconnected)) return;
		GuiScreen screen = event.gui;
		List<GuiButton> buttons = event.buttonList;
		int bottom = screen.height / 2;
		for (GuiButton b : buttons) bottom = Math.max(bottom, b.yPosition + 20);
		int y = bottom + 4;
		deadline = auto.get() ? System.currentTimeMillis() + Math.round(delay.get() * 1000) : 0;
		reconnectButton = new GuiButton(RECONNECT_ID, screen.width / 2 - 100, y, label());
		buttons.add(reconnectButton);
		if (deadline > 0) buttons.add(new GuiButton(CANCEL_ID, screen.width / 2 - 100, y + 24, Lang.get("zerovisual.reconnect.cancel")));
	}

	@SubscribeEvent
	public void onButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
		if (!(event.gui instanceof GuiDisconnected) || reconnectButton == null) return;
		if (event.button.id == RECONNECT_ID) {
			reconnect();
			event.setCanceled(true);
		} else if (event.button.id == CANCEL_ID) {
			deadline = 0;
			reconnectButton.displayString = label();
			event.button.visible = false;
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END || reconnectButton == null || deadline == 0 || !(mc.currentScreen instanceof GuiDisconnected)) return;
		if (System.currentTimeMillis() >= deadline) {
			deadline = 0;
			reconnect();
		} else {
			reconnectButton.displayString = label();
		}
	}

	private String label() {
		if (deadline == 0) return Lang.get("zerovisual.reconnect");
		long seconds = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
		return Lang.get("zerovisual.reconnect.timer", seconds);
	}

	private void reconnect() {
		ServerData server = lastServer;
		reconnectButton = null;
		if (server != null) mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(new GuiMainMenu()), mc, server));
	}
}
