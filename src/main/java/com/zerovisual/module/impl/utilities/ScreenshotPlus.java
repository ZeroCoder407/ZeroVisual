package com.zerovisual.module.impl.utilities;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.module.impl.hud.Notifications;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * After F2, copies the screenshot to the clipboard and shows a notification.
 * Minecraft runs Java in headless mode, so the Windows clipboard is used through PowerShell.
 */
public class ScreenshotPlus extends Module {
	public final BooleanSetting copy = add(new BooleanSetting("Copy to clipboard", true));
	public final BooleanSetting notification = add(new BooleanSetting("Notification", true));

	public ScreenshotPlus() {
		super("Screenshot+", "Копирует скриншот (F2) в буфер обмена", Category.UTILITIES);
		setEnabled(true);
	}

	/** Called when a screenshot is being saved; waits for the file in the background. */
	public void onScreenshot(File gameDirectory) {
		long started = System.currentTimeMillis();
		File folder = new File(gameDirectory, "screenshots");
		Thread thread = new Thread(() -> {
			File file = waitForFile(folder, started);
			if (file == null) return;
			boolean copied = copy.get() && copyToClipboard(file);
			if (notification.get()) {
				Notifications notifications = ZeroVisualClient.get().modules().get(Notifications.class);
				if (copied) notifications.notify("Скриншот", "Скопирован в буфер обмена", true);
				else notifications.notify("Скриншот", "Сохранён: " + file.getName(), true);
			}
		}, "ZeroVisual Screenshot+");
		thread.setDaemon(true);
		thread.start();
	}

	private static File waitForFile(File folder, long since) {
		for (int i = 0; i < 50; i++) {
			File[] files = folder.listFiles((dir, name) -> name.endsWith(".png"));
			if (files != null) {
				Optional<File> newest = Arrays.stream(files)
					.filter(f -> f.lastModified() >= since - 1000 && f.length() > 0)
					.max(Comparator.comparingLong(File::lastModified));
				if (newest.isPresent()) {
					// let the writer finish
					sleep(150);
					return newest.get();
				}
			}
			sleep(100);
		}
		return null;
	}

	private static boolean copyToClipboard(File file) {
		if (Util.getOperatingSystem() != Util.OperatingSystem.WINDOWS) return false;
		String path = file.getAbsolutePath().replace("'", "''");
		String script = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; "
			+ "$img = [System.Drawing.Image]::FromFile('" + path + "'); "
			+ "[System.Windows.Forms.Clipboard]::SetImage($img); $img.Dispose()";
		try {
			Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-Command", script)
				.redirectErrorStream(true)
				.start();
			process.getInputStream().readAllBytes();
			return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
		} catch (IOException | InterruptedException e) {
			ZeroVisualClient.LOGGER.warn("Could not copy the screenshot to the clipboard: {}", e.toString().toLowerCase(Locale.ROOT));
			return false;
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}
}
