package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.PlayWithFriends;
import com.zerovisual.pwf.PwfManager;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.pwf.PwfSession;
import com.zerovisual.render.Render2D;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Play With Friends. Host: opens the world to LAN, shows the invite code and takes the friend's answer code.
 * Friend: takes the invite, shows the answer code and joins once the direct connection is up.
 */
public class PwfScreen extends Screen {
	private static final int W = 320, H = 252;

	@Nullable
	private final Screen parent;
	private final boolean host;
	private PwfSession session;
	private EditBox codeField, inputField;
	private Button actionButton;
	private String notice = "";
	private int noticeColor = Theme.TEXT_MUTED;
	private boolean joinStarted;

	private PwfScreen(@Nullable Screen parent, boolean host) {
		super(Component.translatableWithFallback("zerovisual.pwf.title", "Play with friends"));
		this.parent = parent;
		this.host = host;
	}

	/** From the pause menu of a singleplayer world. */
	public static PwfScreen host(@Nullable Screen parent) {
		return new PwfScreen(parent, true);
	}

	/** From the multiplayer screen. */
	public static PwfScreen join(@Nullable Screen parent) {
		return new PwfScreen(parent, false);
	}

	private int px() {
		return (width - W) / 2;
	}

	private int py() {
		return (height - H) / 2;
	}

	private static Component t(String key, String fallback) {
		return Component.translatableWithFallback("zerovisual.pwf." + key, fallback);
	}

	@Override
	protected void init() {
		int x = px() + 16, y = py();
		if (host) {
			IntegratedServer server = minecraft.getSingleplayerServer();
			if (server == null) {
				onClose();
				return;
			}
			// the same as "Open to LAN"; guests do not get commands
			if (!server.isPublished()) server.publishServer(null, false, HttpUtil.getAvailablePort());
			server.setUsesAuthentication(!noLicense().get());
			if (session == null) session = PwfManager.currentHost(server.getPort());
		} else if (session == null && PwfManager.joining() != null && PwfManager.joining().state() != PwfSession.State.CLOSED
			&& PwfManager.joining().state() != PwfSession.State.FAILED) {
			session = PwfManager.joining();
		}

		if (host) {
			codeField = field(x, y + 52, W - 32 - 84, false);
			addRenderableWidget(Button.builder(t("copy", "Copy"), b -> copyCode()).bounds(x + W - 32 - 78, y + 49, 78, 20).build());
			inputField = field(x, y + 104, W - 32 - 84, true);
			actionButton = addRenderableWidget(Button.builder(t("connect", "Connect"), b -> acceptAnswer()).bounds(x + W - 32 - 78, y + 101, 78, 20).build());
			addRenderableWidget(Button.builder(licenseLabel(), b -> {
				noLicense().set(!noLicense().get());
				var srv = minecraft.getSingleplayerServer();
				if (srv != null) srv.setUsesAuthentication(!noLicense().get());
				ZeroVisualClient.get().config().save();
				b.setMessage(licenseLabel());
			}).bounds(x, y + 172, W - 32, 20).build());
			addRenderableWidget(Button.builder(t("new_friend", "Invite another"), b -> {
				session = PwfManager.newHost(minecraft.getSingleplayerServer().getPort());
				inputField.setValue("");
				setNotice("", 0);
			}).bounds(x, y + H - 32, (W - 40) / 2, 20).build());
		} else {
			inputField = field(x, y + 52, W - 32 - 84, true);
			actionButton = addRenderableWidget(Button.builder(t("next", "Next"), b -> startJoin()).bounds(x + W - 32 - 78, y + 49, 78, 20).build());
			codeField = field(x, y + 104, W - 32 - 84, false);
			addRenderableWidget(Button.builder(t("copy", "Copy"), b -> copyCode()).bounds(x + W - 32 - 78, y + 101, 78, 20).build());
		}
		addRenderableWidget(Button.builder(t("done", "Done"), b -> onClose())
			.bounds(host ? x + (W - 40) / 2 + 8 : x, y + H - 32, host ? (W - 40) / 2 : W - 32, 20).build());
		setInitialFocus(inputField);
	}

	private EditBox field(int x, int y, int w, boolean editable) {
		EditBox f = new EditBox(font, x, y, w, 16, Component.empty());
		f.setMaxLength(600);
		f.setEditable(editable);
		addRenderableWidget(f);
		return f;
	}

	private static BooleanSetting noLicense() {
		return ZeroVisualClient.get().modules().get(PlayWithFriends.class).noLicense;
	}

	private static Component licenseLabel() {
		return t(noLicense().get() ? "license_off" : "license_on", noLicense().get() ? "Without license: ON (anyone with the code)" : "Without license: OFF (licensed friends only)");
	}

	private void copyCode() {
		if (session != null && session.code() != null) {
			minecraft.keyboardHandler.setClipboard(session.code());
			setNotice(t("copied", "Copied, send it in a messenger").getString(), 0xFF4ADE80);
		}
	}

	private void acceptAnswer() {
		if (session == null) return;
		if (session.acceptAnswer(inputField.getValue())) setNotice(t("answer_ok", "Answer accepted, connecting...").getString(), 0xFF4ADE80);
		else setNotice(t("answer_bad", "This is not an answer to this invite").getString(), 0xFFF43F5E);
	}

	private void startJoin() {
		if (joinStarted && session != null && session.state() != PwfSession.State.FAILED) return;
		PwfSession s = PwfSession.join(inputField.getValue(), port -> minecraft.execute(() -> connect(port)));
		if (s == null) {
			setNotice(t("invite_bad", "This is not an invite code").getString(), 0xFFF43F5E);
			return;
		}
		session = s;
		joinStarted = true;
		PwfManager.setJoining(s);
		setNotice("", 0);
	}

	private void connect(int port) {
		ServerData info = new ServerData("ZeroVisual PWF", "127.0.0.1:" + port, ServerData.Type.OTHER);
		ConnectScreen.startConnecting(parent != null ? parent : this, minecraft, new ServerAddress("127.0.0.1", port), info, false, null);
	}

	private void setNotice(String text, int color) {
		notice = text;
		noticeColor = color == 0 ? Theme.TEXT_MUTED : color;
	}

	@Override
	public void tick() {
		if (session != null && session.code() != null && !session.code().equals(codeField.getValue())) {
			codeField.setValue(session.code());
			codeField.moveCursorToStart(false);
		}
	}

	@Override
	public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		int x = px(), y = py();
		Render2D.shadow(ctx, x, y + 4, W, H, 12, 18, 0xA0000000);
		Render2D.rect(ctx, x, y, W, H, 12, Theme.BACKGROUND);
		Render2D.outline(ctx, x, y, W, H, 12, 1, 0x14FFFFFF);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int x = px() + 16, y = py();
		Render2D.text(ctx, title.getString(), x, y + 14, Theme.TEXT);
		Render2D.text(ctx, t("subtitle", "Direct connection, no server or VPN").getString(), x, y + 25, Theme.TEXT_DIM);
		if (host) {
			Render2D.text(ctx, t("host_step1", "1. Send this invite code to a friend").getString(), x, y + 40, Theme.TEXT_MUTED);
			Render2D.text(ctx, t("host_step2", "2. Paste the answer code of the friend").getString(), x, y + 92, Theme.TEXT_MUTED);
		} else {
			Render2D.text(ctx, t("join_step1", "1. Paste the invite code of the friend").getString(), x, y + 40, Theme.TEXT_MUTED);
			Render2D.text(ctx, t("join_step2", "2. Send this answer code back").getString(), x, y + 92, Theme.TEXT_MUTED);
		}
		String status = statusText();
		Render2D.text(ctx, status, x, y + 126, statusColor());
		if (session != null) {
			Render2D.text(ctx, t("net_you", "Your network").getString() + ": " + netText(session.localNet()), x, y + 138, Theme.TEXT_DIM);
			Render2D.text(ctx, t("net_friend", "Friend's network").getString() + ": " + netText(session.remoteNet()), x, y + 148, Theme.TEXT_DIM);
		}
		String vpn = vpnHint();
		String hint = !vpn.isEmpty() ? vpn : !notice.isEmpty() ? notice : failureHint();
		if (!hint.isEmpty()) Render2D.text(ctx, hint, x, y + 160, !vpn.isEmpty() || notice.isEmpty() ? 0xFFFACC15 : noticeColor);
		Render2D.text(ctx, t("privacy", "The code contains your IP: share it only with friends").getString(), x, y + H - 46, Theme.TEXT_DIM);
	}

	private String statusText() {
		if (session == null) return host ? "" : t("status_paste", "Waiting for the invite code").getString();
		return switch (session.state()) {
			case PREPARING -> t("status_preparing", "Finding your address...").getString();
			case WAITING -> "no_public_address".equals(session.message())
				? t("status_local_only", "No internet address found: only works in the same network").getString()
				: host ? t("status_waiting_host", "Waiting for the friend...").getString()
				: t("status_waiting_join", "Waiting for the host to paste your answer...").getString();
			case CONNECTED -> host
				? t("status_connected_host", "Friend connected").getString() + " (" + PwfManager.connectedFriends() + ")"
				: t("status_connected_join", "Connected! Joining the world...").getString();
			case FAILED -> t("status_failed", "Failed").getString() + ": " + t("reason." + session.message(), session.message()).getString();
			case CLOSED -> t("status_closed", "Connection closed").getString();
		};
	}

	/** NAT type and router port forwarding of one side, or "..." while unknown. */
	private static String netText(com.zerovisual.pwf.InviteCode.NetInfo net) {
		if (net == null) return "...";
		String nat = net.publicIndex() < 0 ? t("net_offline", "no internet address").getString()
			: net.symmetric() ? t("net_strict", "strict NAT").getString() : t("net_normal", "normal NAT").getString();
		String port = net.upnp() ? t("net_upnp", "port open (UPnP)").getString()
			: net.cgnat() ? t("net_cgnat", "provider NAT (CGNAT)").getString() : t("net_no_upnp", "no UPnP").getString();
		return nat + ", " + port + (net.vpn() ? ", VPN" : "");
	}

	/** Through a VPN the friend reaches the VPN server instead of this computer: the most common reason it fails. */
	private String vpnHint() {
		if (session == null || session.state() == PwfSession.State.CONNECTED) return "";
		var you = session.localNet();
		var friend = session.remoteNet();
		if (you != null && you.vpn()) return t("hint_vpn_you", "Turn off your VPN while playing, then make a new code").getString();
		if (friend != null && friend.vpn()) return t("hint_vpn_friend", "The friend has a VPN on: ask them to turn it off and make a new code").getString();
		return "";
	}

	/** What to try when the direct connection failed. */
	private String failureHint() {
		if (session == null || session.state() != PwfSession.State.FAILED || !"no_direct".equals(session.message())) return "";
		var you = session.localNet();
		var friend = session.remoteNet();
		if (you != null && friend != null && !you.upnp() && !friend.upnp()) {
			return t("hint_upnp", "Turn on UPnP in the router of one of you, or host from a phone hotspot").getString();
		}
		return t("hint_retry", "Try again: create a new invite and paste the answer quickly").getString();
	}

	private int statusColor() {
		if (session == null) return Theme.TEXT_MUTED;
		return switch (session.state()) {
			case CONNECTED -> 0xFF4ADE80;
			case FAILED -> 0xFFF43F5E;
			default -> Theme.accentLight();
		};
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
