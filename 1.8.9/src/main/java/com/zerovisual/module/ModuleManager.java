package com.zerovisual.module;

import com.zerovisual.module.impl.hud.HudStyle;
import com.zerovisual.module.impl.hud.Notifications;
import com.zerovisual.module.impl.utilities.ClickGui;
import com.zerovisual.render.DrawContext;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleManager {
	private final List<Module> modules = new ArrayList<Module>();

	public void init() {
		// Visuals
		add(new com.zerovisual.module.impl.visuals.FullBright());
		add(new com.zerovisual.module.impl.visuals.Zoom());
		add(new com.zerovisual.module.impl.visuals.NoHurtCam());
		add(new com.zerovisual.module.impl.visuals.Crosshair());
		add(new com.zerovisual.module.impl.visuals.HitColor());
		add(new com.zerovisual.module.impl.visuals.TimeChanger());
		add(new com.zerovisual.module.impl.visuals.BlockOutline());
		add(new com.zerovisual.module.impl.visuals.ViewModel());
		add(new com.zerovisual.module.impl.visuals.Animations());
		add(new com.zerovisual.module.impl.visuals.ItemPhysics());
		add(new com.zerovisual.module.impl.visuals.MotionBlur());
		add(new com.zerovisual.module.impl.visuals.NoWeather());
		add(new com.zerovisual.module.impl.visuals.AspectRatio());
		add(new com.zerovisual.module.impl.visuals.Ambience());
		add(new com.zerovisual.module.impl.visuals.LowFire());
		add(new com.zerovisual.module.impl.visuals.Freelook());
		add(new com.zerovisual.module.impl.visuals.CinematicCamera());
		add(new com.zerovisual.module.impl.visuals.GlintColor());
		add(new com.zerovisual.module.impl.visuals.ChinaHat());
		add(new com.zerovisual.module.impl.visuals.TargetEsp());
		add(new com.zerovisual.module.impl.visuals.CustomSky());
		add(new com.zerovisual.module.impl.visuals.Seasons());
		add(new com.zerovisual.module.impl.visuals.JumpCircles());
		add(new com.zerovisual.module.impl.visuals.DamageNumbers());
		add(new com.zerovisual.module.impl.visuals.Nametags());

		// Cosmetics
		add(new com.zerovisual.module.impl.cosmetics.Cape());
		add(new com.zerovisual.module.impl.cosmetics.EmoteMenu());
		add(new com.zerovisual.module.impl.cosmetics.HitParticles());
		add(new com.zerovisual.module.impl.cosmetics.Trails());
		add(new com.zerovisual.module.impl.cosmetics.FireFlies());
		add(new com.zerovisual.module.impl.cosmetics.Halo());
		add(new com.zerovisual.module.impl.cosmetics.Wings());
		add(new com.zerovisual.module.impl.cosmetics.HitSound());
		add(new com.zerovisual.module.impl.cosmetics.KillEffect());
		add(new com.zerovisual.module.impl.cosmetics.Pet());
		add(new com.zerovisual.module.impl.visuals.HitboxColor());
		add(new com.zerovisual.module.impl.visuals.SmoothSwap());
		add(new com.zerovisual.module.impl.visuals.ItemZoom());

		// HUD
		add(new HudStyle());
		add(new com.zerovisual.module.impl.hud.Watermark());
		add(new com.zerovisual.module.impl.hud.ModuleList());
		add(new com.zerovisual.module.impl.hud.Coordinates());
		add(new com.zerovisual.module.impl.hud.Potions());
		add(new com.zerovisual.module.impl.hud.Keystrokes());
		add(new com.zerovisual.module.impl.hud.ArmorHud());
		add(new com.zerovisual.module.impl.hud.TargetHud());
		add(new com.zerovisual.module.impl.hud.InfoHud());
		add(new Notifications());
		add(new com.zerovisual.module.impl.hud.ChatTweaks());
		add(new com.zerovisual.module.impl.hud.ChatHeads());
		add(new com.zerovisual.module.impl.hud.Saturation());
		add(new com.zerovisual.module.impl.hud.Compass());
		add(new com.zerovisual.module.impl.hud.PlayerView());
		add(new com.zerovisual.module.impl.hud.Minimap());
		add(new com.zerovisual.module.impl.hud.LowHealth());
		add(new com.zerovisual.module.impl.hud.NowPlaying());

		// Utilities
		add(new ClickGui());
		add(new com.zerovisual.module.impl.utilities.MainMenu());
		add(new com.zerovisual.module.impl.utilities.AutoSprint());
		add(new com.zerovisual.module.impl.utilities.Waypoints());
		add(new com.zerovisual.module.impl.utilities.AutoTool());
		add(new com.zerovisual.module.impl.utilities.ScreenshotPlus());
		add(new com.zerovisual.module.impl.utilities.AutoReconnect());
		add(new com.zerovisual.module.impl.utilities.PlayWithFriends());
		add(new com.zerovisual.module.impl.utilities.MusicMenu());
		add(new com.zerovisual.module.impl.performance.EntityCulling());
		add(new com.zerovisual.module.impl.performance.ParticleLimit());
		add(new com.zerovisual.module.impl.performance.CrystalOptimizer());
		add(new com.zerovisual.module.impl.performance.LiteDrops());
	}

	private void add(Module module) {
		modules.add(module);
	}

	public List<Module> getModules() {
		return Collections.unmodifiableList(modules);
	}

	public List<Module> getByCategory(Category category) {
		List<Module> list = new ArrayList<Module>();
		for (Module m : modules) if (m.getCategory() == category) list.add(m);
		return list;
	}

	public <T extends Module> T get(Class<T> type) {
		for (Module module : modules) {
			if (type.isInstance(module)) return type.cast(module);
		}
		throw new IllegalArgumentException("Module not registered: " + type.getSimpleName());
	}

	public void onTick() {
		for (Module module : modules) {
			if (module.isEnabled()) module.onTick();
		}
	}

	public void onKeyPress(int key) {
		if (key == Keyboard.KEY_NONE) return;
		for (Module module : modules) {
			if (module.getKey() == key) module.onKeyPressed();
		}
	}

	public void renderHud(DrawContext ctx, float tickDelta) {
		for (Module module : modules) {
			if (module instanceof HudModule && module.isEnabled()) ((HudModule) module).render(ctx, tickDelta);
		}
	}
}
