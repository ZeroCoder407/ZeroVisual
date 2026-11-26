package com.zerovisual.util;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.render.DrawContext;
import com.zerovisual.render.Render2D;
import net.minecraft.launchwrapper.Launch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
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
	/** Public half of the release signing key (X.509, base64): the raw key is its last 32 bytes. */
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
		Object deobfuscated = Launch.blackboard == null ? null : Launch.blackboard.get("fml.deobfuscatedEnvironment");
		if (Boolean.TRUE.equals(deobfuscated)) {
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
		Render2D.centeredText(ctx, Lang.get("zerovisual.build.unofficial"), ctx.getScaledWindowWidth() / 2f, 2, 0xB0FF5C6C);
	}

	/** Warning at the bottom of the title screen. */
	public static void renderTitleWarning(DrawContext ctx, int width, int height) {
		if (!isUnofficial()) return;
		String title = Lang.get("zerovisual.build.unofficial");
		String hint = Lang.get("zerovisual.build.unofficial.hint");
		float w = Math.max(Render2D.textWidth(title), Render2D.textWidth(hint)) + 16;
		float x = width / 2f - w / 2, y = height - 46;
		Render2D.rect(ctx, x, y, w, 26, 6, 0xC0140A0E);
		Render2D.outline(ctx, x, y, w, 26, 6, 1, 0x80FF5C6C);
		Render2D.centeredText(ctx, title, width / 2f, y + 4, 0xFFFF5C6C);
		Render2D.centeredText(ctx, hint, width / 2f, y + 15, 0xFFB8B8C8);
	}

	/** The jar the mod was loaded from: the code source of this class, "file:" or "jar:file:...!/". */
	private static File jarFile() throws Exception {
		URL location = BuildVerifier.class.getProtectionDomain().getCodeSource().getLocation();
		String url = location.toString();
		if (url.startsWith("jar:")) url = url.substring(4, url.indexOf("!/"));
		return new File(new URL(url).toURI());
	}

	private static boolean verify() {
		try {
			File file = jarFile();
			if (!file.isFile()) return false;
			try (ZipFile jar = new ZipFile(file)) {
				ZipEntry signatureEntry = jar.getEntry(SIGNATURE_ENTRY);
				if (signatureEntry == null) return false;
				byte[] signature;
				try (InputStream in = jar.getInputStream(signatureEntry)) {
					signature = Base64.getDecoder().decode(new String(readAll(in), StandardCharsets.US_ASCII).trim());
				}
				byte[] spki = Base64.getDecoder().decode(PUBLIC_KEY);
				byte[] key = new byte[32];
				System.arraycopy(spki, spki.length - 32, key, 0, 32);
				return Ed25519.verify(key, digest(jar), signature);
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
		List<ZipEntry> entries = new ArrayList<ZipEntry>();
		Enumeration<? extends ZipEntry> all = jar.entries();
		while (all.hasMoreElements()) {
			ZipEntry e = all.nextElement();
			if (!e.isDirectory() && !e.getName().equals(SIGNATURE_ENTRY)) entries.add(e);
		}
		Collections.sort(entries, (a, b) -> a.getName().compareTo(b.getName()));
		MessageDigest total = MessageDigest.getInstance("SHA-256");
		for (ZipEntry entry : entries) {
			total.update(entry.getName().getBytes(StandardCharsets.UTF_8));
			total.update((byte) 0);
			try (InputStream in = jar.getInputStream(entry)) {
				total.update(MessageDigest.getInstance("SHA-256").digest(readAll(in)));
			}
		}
		return total.digest();
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int n;
		while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
		return out.toByteArray();
	}
}
