package com.zerovisual.gui;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.impl.utilities.PlayWithFriends;
import com.zerovisual.pwf.InviteCode;
import com.zerovisual.pwf.PwfManager;
import com.zerovisual.pwf.PwfSession;
import com.zerovisual.render.DrawContext;
import com.zerovisual.render.Render2D;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.util.Lang;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.integrated.IntegratedServer;

/**
 * Play with friends: the host sends an invite code, the friend answers with a code, then both games connect
 * directly. Opened from the pause menu (host) and the multiplayer screen (friend).
 */
public class PwfScreen extends ZvScreen {
	private static final int W = 320, H = 252;

	private final GuiScreen parent;
	private final boolean host;
	private TextField codeField, inputField;
	private Button actionButton;
	private PwfSession session;
	private String notice = "";
	private int noticeColor = Theme.TEXT_MUTED;
	private boolean joinStarted;

	private PwfScreen(GuiScreen parent, boolean host) {
		this.parent = parent;
		this.host = host;
	}

	/** From the pause menu of a singleplayer world. */
	public static PwfScreen host(GuiScreen parent) {
		return new PwfScreen(parent, true);
	}

	/** From the multiplayer screen. */
	public static PwfScreen join(GuiScreen parent) {
		return new PwfScreen(parent, false);
	}

	private int px() {
		return (width - W) / 2;
	}

	private int py() {
		return (height - H) / 2;
	}

	private static String t(String key, String fallback) {
		return Lang.orElse("zerovisual.pwf." + key, fallback);
	}

	@Override
	protected void init() {
		int x = px() + 16, y = py();
		if (host) {
			IntegratedServer server = client.getIntegratedServer();
			if (server == null) {
				close();
				return;
			}
			// the same as "Open to LAN", keeping the game mode and the cheat setting of the world
			// the port comes back through IntegratedServerMixin
			if (!server.getPublic()) server.shareToLAN(client.playerController.getCurrentGameType(), client.theWorld.getWorldInfo().areCommandsAllowed());
			server.setOnlineMode(!noLicense().get());
			if (session == null) session = PwfManager.currentHost(PwfManager.lanPort());
		} else if (session == null && PwfManager.joining() != null && PwfManager.joining().state() != PwfSession.State.CLOSED
			&& PwfManager.joining().state() != PwfSession.State.FAILED) {
			session = PwfManager.joining();
		}

		if (host) {
			codeField = field(x, y + 52, W - 32 - 84, false);
			addWidget(new Button(t("copy", "Copy"), x + W - 32 - 78, y + 49, 78, 20, b -> copyCode()));
			inputField = field(x, y + 104, W - 32 - 84, true);
			actionButton = addWidget(new Button(t("connect", "Connect"), x + W - 32 - 78, y + 101, 78, 20, b -> acceptAnswer()));
			addWidget(new Button(licenseLabel(), x, y + 172, W - 32, 20, b -> {
				noLicense().set(!noLicense().get());
				IntegratedServer srv = client.getIntegratedServer();
				if (srv != null) srv.setOnlineMode(!noLicense().get());
				ZeroVisualClient.get().config().save();
				b.setMessage(licenseLabel());
			}));
			addWidget(new Button(t("new_friend", "Invite another"), x, y + H - 32, (W - 40) / 2, 20, b -> {
				session = PwfManager.newHost(PwfManager.lanPort());
				inputField.setText("");
				setNotice("", 0);
			}));
		} else {
			inputField = field(x, y + 52, W - 32 - 84, true);
			actionButton = addWidget(new Button(t("next", "Next"), x + W - 32 - 78, y + 49, 78, 20, b -> startJoin()));
			codeField = field(x, y + 104, W - 32 - 84, false);
			addWidget(new Button(t("copy", "Copy"), x + W - 32 - 78, y + 101, 78, 20, b -> copyCode()));
		}
		addWidget(new Button(t("done", "Done"), host ? x + (W - 40) / 2 + 8 : x, y + H - 32, host ? (W - 40) / 2 : W - 32, 20, b -> close()));
		inputField.setFocused(true);
	}

	private TextField field(int x, int y, int w, boolean editable) {
		TextField f = new TextField(x + 4, y + 4, w - 8, 10);
		f.setMaxLength(600);
		f.setEditable(editable);
		addWidget(new FieldFrame(f, x, y, w, 16));
		return addWidget(f);
	}

	/** The dark box behind a text field. */
	private static final class FieldFrame extends Widget {
		private final TextField field;

		FieldFrame(TextField field, float x, float y, float w, float h) {
			super(x, y, w, h);
			this.field = field;
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY) {
			Render2D.rect(ctx, x, y, width, height, 5, Theme.SURFACE);
			Render2D.outline(ctx, x, y, width, height, 5, 1, field.isFocused() ? Theme.accent() : 0x18FFFFFF);
		}
	}

	private static BooleanSetting noLicense() {
		return ZeroVisualClient.get().modules().get(PlayWithFriends.class).noLicense;
	}

	private static String licenseLabel() {
		return t(noLicense().get() ? "license_off" : "license_on", noLicense().get() ? "Without license: ON (anyone with the code)" : "Without license: OFF (licensed friends only)");
	}

	private void copyCode() {
		if (session != null && session.code() != null) {
			GuiScreen.setClipboardString(session.code());
			setNotice(t("copied", "Copied, send it in a messenger"), 0xFF4ADE80);
		}
	}

	private void acceptAnswer() {
		if (session == null) return;
		if (session.acceptAnswer(inputField.getText())) setNotice(t("answer_ok", "Answer accepted, connecting..."), 0xFF4ADE80);
		else setNotice(t("answer_bad", "This is not an answer to this invite"), 0xFFF43F5E);
	}

	private void startJoin() {
		if (joinStarted && session != null && session.state() != PwfSession.State.FAILED) return;
		PwfSession s = PwfSession.join(inputField.getText(), port -> client.addScheduledTask(() -> connect(port)));
		if (s == null) {
			setNotice(t("invite_bad", "This is not an invite code"), 0xFFF43F5E);
			return;
		}
		session = s;
		joinStarted = true;
		PwfManager.setJoining(s);
		setNotice("", 0);
	}

	private void connect(int port) {
		ServerData info = new ServerData("ZeroVisual PWF", "127.0.0.1:" + port, false);
		client.displayGuiScreen(new GuiConnecting(parent != null ? parent : this, client, info));
	}

	private void setNotice(String text, int color) {
		notice = text;
		noticeColor = color == 0 ? Theme.TEXT_MUTED : color;
	}

	@Override
	public void tick() {
		if (session != null && session.code() != null && !session.code().equals(codeField.getText())) codeField.setText(session.code());
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		int x = px(), y = py();
		Render2D.shadow(ctx, x, y + 4, W, H, 12, 18, 0xA0000000);
		Render2D.rect(ctx, x, y, W, H, 12, Theme.BACKGROUND);
		Render2D.outline(ctx, x, y, W, H, 12, 1, 0x14FFFFFF);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int x = px() + 16, y = py();
		Render2D.text(ctx, t("title", "Play with friends"), x, y + 14, Theme.TEXT);
		Render2D.text(ctx, t("subtitle", "Direct connection, no server or VPN"), x, y + 25, Theme.TEXT_DIM);
		if (host) {
			Render2D.text(ctx, t("host_step1", "1. Send this invite code to a friend"), x, y + 40, Theme.TEXT_MUTED);
			Render2D.text(ctx, t("host_step2", "2. Paste the answer code of the friend"), x, y + 92, Theme.TEXT_MUTED);
		} else {
			Render2D.text(ctx, t("join_step1", "1. Paste the invite code of the friend"), x, y + 40, Theme.TEXT_MUTED);
			Render2D.text(ctx, t("join_step2", "2. Send this answer code back"), x, y + 92, Theme.TEXT_MUTED);
		}
		Render2D.text(ctx, statusText(), x, y + 126, statusColor());
		if (session != null) {
			Render2D.text(ctx, t("net_you", "Your network") + ": " + netText(session.localNet()), x, y + 138, Theme.TEXT_DIM);
			Render2D.text(ctx, t("net_friend", "Friend's network") + ": " + netText(session.remoteNet()), x, y + 148, Theme.TEXT_DIM);
		}
		String vpn = vpnHint();
		String hint = !vpn.isEmpty() ? vpn : !notice.isEmpty() ? notice : failureHint();
		if (!hint.isEmpty()) Render2D.text(ctx, hint, x, y + 160, !vpn.isEmpty() || notice.isEmpty() ? 0xFFFACC15 : noticeColor);
		Render2D.text(ctx, t("privacy", "The code contains your IP: share it only with friends"), x, y + H - 46, Theme.TEXT_DIM);
	}

	private String statusText() {
		if (session == null) return host ? "" : t("status_paste", "Waiting for the invite code");
		switch (session.state()) {
			case PREPARING:
				return t("status_preparing", "Finding your address...");
			case WAITING:
				if ("no_public_address".equals(session.message())) return t("status_local_only", "No internet address found: only works in the same network");
				return host ? t("status_waiting_host", "Waiting for the friend...") : t("status_waiting_join", "Waiting for the host to paste your answer...");
			case CONNECTED:
				return host ? t("status_connected_host", "Friend connected") + " (" + PwfManager.connectedFriends() + ")"
					: t("status_connected_join", "Connected! Joining the world...");
			case FAILED:
				return t("status_failed", "Failed") + ": " + t("reason." + session.message(), session.message());
			default:
				return t("status_closed", "Connection closed");
		}
	}

	/** NAT type and router port forwarding of one side, or "..." while unknown. */
	private static String netText(InviteCode.NetInfo net) {
		if (net == null) return "...";
		String nat = net.publicIndex() < 0 ? t("net_offline", "no internet address")
			: net.symmetric() ? t("net_strict", "strict NAT") : t("net_normal", "normal NAT");
		String port = net.upnp() ? t("net_upnp", "port open (UPnP)")
			: net.cgnat() ? t("net_cgnat", "provider NAT (CGNAT)") : t("net_no_upnp", "no UPnP");
		return nat + ", " + port + (net.vpn() ? ", VPN" : "");
	}

	/** Through a VPN the friend reaches the VPN server instead of this computer: the most common reason it fails. */
	private String vpnHint() {
		if (session == null || session.state() == PwfSession.State.CONNECTED) return "";
		InviteCode.NetInfo you = session.localNet(), friend = session.remoteNet();
		if (you != null && you.vpn()) return t("hint_vpn_you", "Turn off your VPN while playing, then make a new code");
		if (friend != null && friend.vpn()) return t("hint_vpn_friend", "The friend has a VPN on: ask them to turn it off and make a new code");
		return "";
	}

	/** What to try when the direct connection failed. */
	private String failureHint() {
		if (session == null || session.state() != PwfSession.State.FAILED || !"no_direct".equals(session.message())) return "";
		InviteCode.NetInfo you = session.localNet(), friend = session.remoteNet();
		if (you != null && friend != null && !you.upnp() && !friend.upnp()) {
			return t("hint_upnp", "Turn on UPnP in the router of one of you, or host from a phone hotspot");
		}
		return t("hint_retry", "Try again: create a new invite and paste the answer quickly");
	}

	private int statusColor() {
		if (session == null) return Theme.TEXT_MUTED;
		switch (session.state()) {
			case CONNECTED:
				return 0xFF4ADE80;
			case FAILED:
				return 0xFFF43F5E;
			default:
				return Theme.accentLight();
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		client.displayGuiScreen(parent);
	}
}
