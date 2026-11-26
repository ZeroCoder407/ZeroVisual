package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.PlayWithFriends;
import com.zerovisual.pwf.PwfManager;
import com.zerovisual.util.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/** The buttons ZeroVisual adds to vanilla screens: Play with friends in the pause and multiplayer menus. */
public final class ScreenHooks {
	private static final int HOST_ID = 0x7B01, JOIN_ID = 0x7B02;

	@SubscribeEvent
	public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (event.gui instanceof net.minecraft.client.gui.GuiMainMenu && ZeroVisualClient.get().modules().get(com.zerovisual.module.impl.utilities.MainMenu.class).accountButton.get()) {
			event.buttonList.add(new AccountButton(6, 6));
		}
		if (!ZeroVisualClient.get().modules().get(PlayWithFriends.class).isEnabled()) return;
		if (event.gui instanceof GuiIngameMenu && mc.isIntegratedServerRunning()) {
			event.buttonList.add(new GuiButton(HOST_ID, event.gui.width - 136, 6, 130, 20, Lang.orElse("zerovisual.pwf.button_host", "Play with friends")));
		}
		if (event.gui instanceof GuiMultiplayer) {
			event.buttonList.add(new GuiButton(JOIN_ID, event.gui.width - 136, 6, 130, 20, Lang.orElse("zerovisual.pwf.button_join", "Join a friend")));
		}
	}

	@SubscribeEvent
	public void onButton(GuiScreenEvent.ActionPerformedEvent.Pre event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (event.button.id == AccountButton.ID && event.gui instanceof net.minecraft.client.gui.GuiMainMenu) {
			mc.displayGuiScreen(new AccountScreen(event.gui));
			event.setCanceled(true);
			return;
		}
		if (event.button.id == HOST_ID && event.gui instanceof GuiIngameMenu) {
			mc.displayGuiScreen(PwfScreen.host(event.gui));
			event.setCanceled(true);
		} else if (event.button.id == JOIN_ID && event.gui instanceof GuiMultiplayer) {
			mc.displayGuiScreen(PwfScreen.join(event.gui));
			event.setCanceled(true);
		}
	}

	/** Leaving the world disconnects the invited friends. */
	@SubscribeEvent
	public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
		PwfManager.closeHosted();
	}
}
