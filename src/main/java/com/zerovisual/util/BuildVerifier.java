package com.zerovisual.util;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.render.Render2D;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Tells official ZeroVisual builds from rebuilt or modified ones.
 * <p>
 * Official jars carry {@value #SIGNATURE_ENTRY}: an Ed25519 signature, made with the project's private key,
 * of a digest over every other file in the jar (see the {@code signModJar} task in build.gradle, which computes
 * the same digest). Any build without a valid signature shows "Unofficial build" on screen.
 * <p>
 * This is not protection: ZeroVisual is free software under GPL-3.0 and anyone may change it, including
 * removing this check. It only makes unmodified official builds recognizable, so a modified client
 * (for example one with cheats added) is not mistaken for ZeroVisual. See TRADEMARKS.md.
 */
public final class BuildVerifier {
	public static final String SIGNATURE_ENTRY = "META-INF/zerovisual.sig";
	/** Public half of the release signing key (X.509, base64). */
	private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEA3A6uP7I92SVsx2GOJT0y+J8EAPvca5qjNQsTciEhd9w=";

	public enum Status {
		CHECKING, OFFICIAL, UNOFFICIAL, DEVELOPMENT
	}

	private static volatile Status status = Status.CHECKING;

	private BuildVerifier() {
	}

	public static Status status() {
		return status;
	}

	public static boolean isUnofficial() {
		return status == Status.UNOFFICIAL;
	}

	/** Checks the signature on a background thread (reading the jar takes a moment). */
	public static void start() {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			status = Status.DEVELOPMENT;
			return;
		}
		Thread thread = new Thread(() -> {
			status = verify() ? Status.OFFICIAL : Status.UNOFFICIAL;
			if (status == Status.UNOFFICIAL) {
				ZeroVisualClient.LOGGER.warn("This is not an official ZeroVisual build. Official builds: https://github.com/ZeroCoder407/ZeroVisual/releases");
			} else {
				ZeroVisualClient.LOGGER.info("Official ZeroVisual build (signature verified)");
			}
		}, "ZeroVisual build check");
		thread.setDaemon(true);
		thread.start();
	}

	// ---- on screen ----

	/** Small mark at the top of the in-game HUD, visible on screenshots and videos. */
	public static void renderHudMark(DrawContext ctx) {
		if (!isUnofficial()) return;
		String text = I18n.translate("zerovisual.build.unofficial");
		Render2D.centeredText(ctx, text, ctx.getScaledWindowWidth() / 2f, 2, 0xB0FF5C6C);
	}

	/** Warning at the bottom of the title screen. */
	public static void renderTitleWarning(DrawContext ctx, int width, int height) {
		if (!isUnofficial()) return;
		String title = I18n.translate("zerovisual.build.unofficial");
		String hint = I18n.translate("zerovisual.build.unofficial.hint");
		float w = Math.max(Render2D.textWidth(title), Render2D.textWidth(hint)) + 16;
		float x = width / 2f - w / 2, y = height - 46;
		Render2D.rect(ctx, x, y, w, 26, 6, 0xC0140A0E);
		Render2D.outline(ctx, x, y, w, 26, 6, 1, 0x80FF5C6C);
		Render2D.centeredText(ctx, title, width / 2f, y + 4, 0xFFFF5C6C);
		Render2D.centeredText(ctx, hint, width / 2f, y + 15, 0xFFB8B8C8);
	}

	private static boolean verify() {
		try {
			Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(ZeroVisualClient.MOD_ID);
			if (mod.isEmpty() || mod.get().getOrigin().getKind() != ModOrigin.Kind.PATH) return false;
			List<Path> paths = mod.get().getOrigin().getPaths();
			if (paths.size() != 1 || !Files.isRegularFile(paths.get(0))) return false;

			try (ZipFile jar = new ZipFile(paths.get(0).toFile())) {
				ZipEntry signatureEntry = jar.getEntry(SIGNATURE_ENTRY);
				if (signatureEntry == null) return false;
				byte[] signatureBytes;
				try (InputStream in = jar.getInputStream(signatureEntry)) {
					signatureBytes = Base64.getDecoder().decode(new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim());
				}
				PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
				Signature signature = Signature.getInstance("Ed25519");
				signature.initVerify(key);
				signature.update(digest(jar));
				return signature.verify(signatureBytes);
			}
		} catch (Exception e) {
			ZeroVisualClient.LOGGER.warn("Could not verify the ZeroVisual build", e);
			return false;
		}
	}

	/**
	 * SHA-256 over all files except the signature, sorted by name: name, a zero byte, SHA-256 of the content.
	 * Must stay identical to the digest in build.gradle.
	 */
	private static byte[] digest(ZipFile jar) throws IOException, java.security.NoSuchAlgorithmException {
		List<ZipEntry> entries = new ArrayList<>();
		jar.stream().filter(e -> !e.isDirectory() && !e.getName().equals(SIGNATURE_ENTRY)).forEach(entries::add);
		entries.sort(Comparator.comparing(ZipEntry::getName));
		MessageDigest total = MessageDigest.getInstance("SHA-256");
		for (ZipEntry entry : entries) {
			total.update(entry.getName().getBytes(StandardCharsets.UTF_8));
			total.update((byte) 0);
			try (InputStream in = jar.getInputStream(entry)) {
				total.update(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
			}
		}
		return total.digest();
	}
}
