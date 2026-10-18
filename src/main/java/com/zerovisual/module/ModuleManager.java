package com.zerovisual.module;

import com.zerovisual.module.impl.cosmetics.Pet;
import com.zerovisual.module.impl.cosmetics.TotemPop;
import com.zerovisual.module.impl.cosmetics.Cape;
import com.zerovisual.module.impl.cosmetics.HitParticles;
import com.zerovisual.module.impl.cosmetics.FireFlies;
import com.zerovisual.module.impl.cosmetics.Halo;
import com.zerovisual.module.impl.cosmetics.HitSound;
import com.zerovisual.module.impl.cosmetics.Wings;
import com.zerovisual.module.impl.cosmetics.KillEffect;
import com.zerovisual.module.impl.cosmetics.Trails;
import com.zerovisual.module.impl.hud.Compass;
import com.zerovisual.module.impl.hud.ChatHeads;
import com.zerovisual.module.impl.hud.LowHealth;
import com.zerovisual.module.impl.hud.Minimap;
import com.zerovisual.module.impl.hud.PlayerView;
import com.zerovisual.module.impl.visuals.CustomSky;
import com.zerovisual.module.impl.visuals.GlintColor;
import com.zerovisual.module.impl.visuals.MotionBlur;
import com.zerovisual.module.impl.visuals.Nametags;
import com.zerovisual.module.impl.visuals.DamageNumbers;
import com.zerovisual.module.impl.visuals.Ambience;
import com.zerovisual.module.impl.visuals.ItemPhysics;
import com.zerovisual.module.impl.hud.ArmorHud;
import com.zerovisual.module.impl.hud.ChatTweaks;
import com.zerovisual.module.impl.hud.InfoHud;
import com.zerovisual.module.impl.hud.Saturation;
import com.zerovisual.module.impl.visuals.Freelook;
import com.zerovisual.module.impl.visuals.TargetEsp;
import com.zerovisual.module.impl.hud.Notifications;
import com.zerovisual.module.impl.hud.TotemCounter;
import com.zerovisual.module.impl.utilities.AutoReconnect;
import com.zerovisual.module.impl.utilities.AutoSprint;
import com.zerovisual.module.impl.utilities.AutoTool;
import com.zerovisual.module.impl.utilities.ScreenshotPlus;
import com.zerovisual.module.impl.utilities.Waypoints;
import com.zerovisual.module.impl.visuals.LowFire;
import com.zerovisual.module.impl.visuals.AspectRatio;
import com.zerovisual.module.impl.hud.Cooldowns;
import com.zerovisual.module.impl.hud.Coordinates;
import com.zerovisual.module.impl.hud.Keystrokes;
import com.zerovisual.module.impl.hud.ModuleList;
import com.zerovisual.module.impl.hud.Potions;
import com.zerovisual.module.impl.hud.TargetHud;
import com.zerovisual.module.impl.hud.Watermark;
import com.zerovisual.module.impl.utilities.ClickGui;
import com.zerovisual.module.impl.utilities.MainMenu;
import com.zerovisual.module.impl.visuals.Animations;
import com.zerovisual.module.impl.visuals.BlockOutline;
import com.zerovisual.module.impl.visuals.ChinaHat;
import com.zerovisual.module.impl.visuals.NoWeather;
import com.zerovisual.module.impl.visuals.ViewModel;
import com.zerovisual.module.impl.visuals.Crosshair;
import com.zerovisual.module.impl.visuals.FullBright;
import com.zerovisual.module.impl.visuals.HitColor;
import com.zerovisual.module.impl.visuals.JumpCircles;
import com.zerovisual.module.impl.visuals.NoHurtCam;
import com.zerovisual.module.impl.visuals.TimeChanger;
import com.zerovisual.module.impl.visuals.Zoom;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleManager {
	private final List<Module> modules = new ArrayList<>();

	public void init() {
		// Visuals
		add(new FullBright());
		add(new Zoom());
		add(new NoHurtCam());
		add(new Crosshair());
		add(new HitColor());
		add(new JumpCircles());
		add(new ChinaHat());
		add(new TimeChanger());
		add(new BlockOutline());
		add(new ViewModel());
		add(new NoWeather());
		add(new Animations());
		add(new AspectRatio());
		add(new ItemPhysics());
		add(new Ambience());
		add(new LowFire());
		add(new Freelook());
		add(new TargetEsp());
		add(new DamageNumbers());
		add(new Nametags());
		add(new MotionBlur());
		add(new GlintColor());
		add(new CustomSky());

		// Cosmetics
		add(new Cape());
		add(new HitParticles());
		add(new Trails());
		add(new FireFlies());
		add(new Halo());
		add(new KillEffect());
		add(new Wings());
		add(new HitSound());
		add(new TotemPop());
		add(new Pet());

		// HUD
		add(new Watermark());
		add(new ModuleList());
		add(new Coordinates());
		add(new Potions());
		add(new TargetHud());
		add(new Keystrokes());
		add(new ArmorHud());
		add(new Cooldowns());
		add(new Notifications());
		add(new TotemCounter());
		add(new InfoHud());
		add(new ChatTweaks());
		add(new ChatHeads());
		add(new Saturation());
		add(new Compass());
		add(new PlayerView());
		add(new Minimap());
		add(new LowHealth());

		// Utilities
		add(new ClickGui());
		add(new MainMenu());
		add(new AutoSprint());
		add(new Waypoints());
		add(new AutoTool());
		add(new ScreenshotPlus());
		add(new AutoReconnect());
	}

	private void add(Module module) {
		modules.add(module);
	}

	public List<Module> getModules() {
		return Collections.unmodifiableList(modules);
	}

	public List<Module> getByCategory(Category category) {
		return modules.stream().filter(m -> m.getCategory() == category).toList();
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
		if (key == GLFW.GLFW_KEY_UNKNOWN) return;
		for (Module module : modules) {
			if (module.getKey() == key) module.onKeyPressed();
		}
	}

	public void renderHud(DrawContext ctx, float tickDelta) {
		for (Module module : modules) {
			if (module instanceof HudModule hud && hud.isEnabled()) hud.render(ctx, tickDelta);
		}
	}
}
