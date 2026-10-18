package com.zerovisual;

import com.zerovisual.account.AccountManager;
import com.zerovisual.config.ConfigManager;
import com.zerovisual.gui.AccountButton;
import com.zerovisual.gui.HudEditor;
import com.zerovisual.module.ModuleManager;
import com.zerovisual.module.impl.cosmetics.Pet;
import com.zerovisual.module.impl.cosmetics.TotemPop;
import com.zerovisual.module.impl.hud.LowHealth;
import com.zerovisual.module.impl.cosmetics.HitParticles;
import com.zerovisual.module.impl.cosmetics.FireFlies;
import com.zerovisual.module.impl.cosmetics.Halo;
import com.zerovisual.module.impl.cosmetics.HitSound;
import com.zerovisual.module.impl.cosmetics.Wings;
import com.zerovisual.module.impl.cosmetics.KillEffect;
import com.zerovisual.module.impl.cosmetics.Trails;
import com.zerovisual.module.impl.visuals.Nametags;
import com.zerovisual.module.impl.visuals.DamageNumbers;
import com.zerovisual.module.impl.visuals.TargetEsp;
import com.zerovisual.util.BuildVerifier;
import com.zerovisual.util.TargetTracker;
import com.zerovisual.module.impl.utilities.AutoReconnect;
import com.zerovisual.module.impl.utilities.AutoTool;
import com.zerovisual.module.impl.utilities.MainMenu;
import com.zerovisual.module.impl.utilities.Waypoints;
import com.zerovisual.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.zerovisual.module.impl.visuals.BlockOutline;
import com.zerovisual.module.impl.visuals.ChinaHat;
import com.zerovisual.module.impl.visuals.JumpCircles;
import com.zerovisual.module.impl.visuals.Freelook;
import com.zerovisual.module.impl.visuals.Zoom;
import com.zerovisual.util.DevScreenshots;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.ActionResult;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.TitleScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeroVisualClient implements ClientModInitializer {
	public static final String MOD_ID = "zerovisual";
	public static final String NAME = "ZeroVisual";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	private static ZeroVisualClient instance;

	private ModuleManager modules;
	private ConfigManager config;
	private AccountManager accounts;
	private WaypointManager waypoints;

	@Override
	public void onInitializeClient() {
		instance = this;
		BuildVerifier.start();

		Zoom.registerKeyBinding();
		Freelook.registerKeyBinding();
		Waypoints.registerKeyBinding();

		modules = new ModuleManager();
		modules.init();
		config = new ConfigManager(modules);
		config.load();
		accounts = new AccountManager();
		accounts.load();
		waypoints = new WaypointManager();
		waypoints.load();

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> modules.get(AutoReconnect.class).onJoin());

		HudEditor.register();
		DevScreenshots.registerIfRequested();

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen && modules.get(MainMenu.class).accountButton.get()) {
				Screens.getButtons(screen).add(new AccountButton(screen, 6, 6));
			}
			if (AutoReconnect.isDisconnectScreen(screen)) modules.get(AutoReconnect.class).onDisconnectedScreen(screen);
			if (screen instanceof TitleScreen) {
				ScreenEvents.afterRender(screen).register((s2, ctx, mouseX, mouseY, delta) -> BuildVerifier.renderTitleWarning(ctx, s2.width, s2.height));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> modules.onTick());

		HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.options.hudHidden || mc.inGameHud.getDebugHud().shouldShowDebugHud()) return;
			// above the chat: it draws its text at z=50 with depth testing, which would hide our text where they overlap
			ctx.getMatrices().push();
			ctx.getMatrices().translate(0, 0, 200);
			modules.renderHud(ctx, tickCounter.getTickDelta(false));
			AutoTool autoTool = modules.get(AutoTool.class);
			if (autoTool.isEnabled()) autoTool.render(ctx);
			BuildVerifier.renderHudMark(ctx);
			LowHealth lowHealth = modules.get(LowHealth.class);
			if (lowHealth.isEnabled()) lowHealth.render(ctx);
			TotemPop totemPop = modules.get(TotemPop.class);
			if (totemPop.isEnabled()) totemPop.renderHud(ctx);
			ctx.getMatrices().pop();
		});

		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			JumpCircles jumpCircles = modules.get(JumpCircles.class);
			if (jumpCircles.isEnabled()) jumpCircles.render(context);
			Pet pet = modules.get(Pet.class);
			if (pet.isEnabled()) pet.render(context);
			TotemPop totemPopWorld = modules.get(TotemPop.class);
			if (totemPopWorld.isEnabled()) totemPopWorld.renderWorld(context);
			ChinaHat chinaHat = modules.get(ChinaHat.class);
			if (chinaHat.isEnabled()) chinaHat.render(context);
			HitParticles hitParticles = modules.get(HitParticles.class);
			if (hitParticles.isEnabled()) hitParticles.render(context);
			Trails trails = modules.get(Trails.class);
			if (trails.isEnabled()) trails.render(context);
			TargetEsp targetEsp = modules.get(TargetEsp.class);
			if (targetEsp.isEnabled()) targetEsp.render(context);
			FireFlies fireFlies = modules.get(FireFlies.class);
			if (fireFlies.isEnabled()) fireFlies.render(context);
			Halo halo = modules.get(Halo.class);
			if (halo.isEnabled()) halo.render(context);
			KillEffect killEffect = modules.get(KillEffect.class);
			if (killEffect.isEnabled()) killEffect.render(context);
			DamageNumbers damageNumbers = modules.get(DamageNumbers.class);
			if (damageNumbers.isEnabled()) damageNumbers.render(context);
			Wings wings = modules.get(Wings.class);
			if (wings.isEnabled()) wings.render(context);
		});

		WorldRenderEvents.END.register(context -> {
			Nametags nametags = modules.get(Nametags.class);
			if (nametags.isEnabled()) nametags.render(context);
			Waypoints waypointsModule = modules.get(Waypoints.class);
			if (waypointsModule.isEnabled()) waypointsModule.render(context);
		});

		WorldRenderEvents.BLOCK_OUTLINE.register((context, outline) -> {
			BlockOutline blockOutline = modules.get(BlockOutline.class);
			return !blockOutline.isEnabled() || blockOutline.render(context, outline);
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient) {
				TargetTracker.onAttack(entity);
				HitParticles hitParticles = modules.get(HitParticles.class);
				if (hitParticles.isEnabled()) hitParticles.spawn(entity);
				HitSound hitSound = modules.get(HitSound.class);
				if (hitSound.isEnabled()) hitSound.onAttack(entity);
			}
			return ActionResult.PASS;
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());

		LOGGER.info("{} initialized with {} modules", NAME, modules.getModules().size());
	}

	public static ZeroVisualClient get() {
		return instance;
	}

	public ModuleManager modules() {
		return modules;
	}

	public ConfigManager config() {
		return config;
	}

	public AccountManager accounts() {
		return accounts;
	}

	public WaypointManager waypoints() {
		return waypoints;
	}
}
