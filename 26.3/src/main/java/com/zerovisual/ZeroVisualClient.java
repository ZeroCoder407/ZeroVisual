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
import com.zerovisual.util.HitColorTexture;
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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import com.zerovisual.render.WorldContext;
import com.zerovisual.render.WorldRender;
import com.zerovisual.render.Render2D;
import com.zerovisual.module.impl.utilities.ClickGui;
import com.zerovisual.render.ZvPipelines;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeroVisualClient implements ClientModInitializer {
	public static final String MOD_ID = "zerovisual";
	public static final String NAME = "ZeroVisual";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	/** Controls category of the ZeroVisual key mappings. */
	public static final net.minecraft.client.KeyMapping.Category KEY_CATEGORY =
		net.minecraft.client.KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static ZeroVisualClient instance;

	private ModuleManager modules;
	private ConfigManager config;
	private AccountManager accounts;
	private WaypointManager waypoints;

	@Override
	public void onInitializeClient() {
		instance = this;
		ZvPipelines.init();
		BuildVerifier.start();

		Zoom.registerKeyBinding();
		Freelook.registerKeyBinding();
		Waypoints.registerKeyBinding();

		modules = new ModuleManager();
		modules.init();
		Render2D.setFontProvider(() -> modules.get(ClickGui.class).getFontId());
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
			if (modules.get(com.zerovisual.module.impl.utilities.PlayWithFriends.class).isEnabled()) {
				if (screen instanceof net.minecraft.client.gui.screens.PauseScreen && client.hasSingleplayerServer()) {
					Screens.getWidgets(screen).add(net.minecraft.client.gui.components.Button.builder(
						net.minecraft.network.chat.Component.translatableWithFallback("zerovisual.pwf.button_host", "Play with friends"),
						b -> client.gui.setScreen(com.zerovisual.gui.PwfScreen.host(screen))).bounds(scaledWidth - 136, 6, 130, 20).build());
				}
				if (screen instanceof net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen) {
					Screens.getWidgets(screen).add(net.minecraft.client.gui.components.Button.builder(
						net.minecraft.network.chat.Component.translatableWithFallback("zerovisual.pwf.button_join", "Join a friend"),
						b -> client.gui.setScreen(com.zerovisual.gui.PwfScreen.join(screen))).bounds(scaledWidth - 136, 6, 130, 20).build());
				}
			}
			if (screen instanceof TitleScreen && modules.get(MainMenu.class).accountButton.get()) {
				Screens.getWidgets(screen).add(new AccountButton(screen, 6, 6));
			}
			if (AutoReconnect.isDisconnectScreen(screen)) modules.get(AutoReconnect.class).onDisconnectedScreen(screen);
			if (screen instanceof TitleScreen) {
				ScreenEvents.afterExtract(screen).register((s2, ctx, mouseX, mouseY, delta) -> BuildVerifier.renderTitleWarning(ctx, s2.width, s2.height));
			}
		});

		// leaving the world disconnects the invited friends
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> com.zerovisual.pwf.PwfManager.closeHosted());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			modules.onTick();
			// texture uploads happen here, never inside a render pass
			((HitColorTexture) client.gameRenderer.overlayTexture()).zerovisual$refresh();
		});

		// added last: drawn above the vanilla HUD and the chat
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (ctx, tickCounter) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
			ctx.pose().pushMatrix();
			modules.renderHud(ctx, tickCounter.getGameTimeDeltaPartialTick(false));
			AutoTool autoTool = modules.get(AutoTool.class);
			if (autoTool.isEnabled()) autoTool.render(ctx);
			BuildVerifier.renderHudMark(ctx);
			LowHealth lowHealth = modules.get(LowHealth.class);
			if (lowHealth.isEnabled()) lowHealth.render(ctx);
			TotemPop totemPop = modules.get(TotemPop.class);
			if (totemPop.isEnabled()) totemPop.renderHud(ctx);
			ctx.pose().popMatrix();
		});

		LevelRenderEvents.COLLECT_SUBMITS.register(levelContext -> {
			if (Minecraft.getInstance().player == null) return;
			WorldRender.begin(levelContext);
			WorldContext context = WorldContext.of(levelContext);
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
			com.zerovisual.module.impl.visuals.HitboxColor hitboxColor = modules.get(com.zerovisual.module.impl.visuals.HitboxColor.class);
			if (hitboxColor.isEnabled()) hitboxColor.render(context);
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
			// name tags and waypoints last: they are drawn on top
			Nametags nametags = modules.get(Nametags.class);
			if (nametags.isEnabled()) nametags.render(context);
			Waypoints waypointsModule = modules.get(Waypoints.class);
			if (waypointsModule.isEnabled()) waypointsModule.render(context);
			WorldRender.end();
		});

		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((levelContext, outline) -> {
			BlockOutline blockOutline = modules.get(BlockOutline.class);
			return !blockOutline.isEnabled() || blockOutline.render(levelContext, outline);
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide()) {
				TargetTracker.onAttack(entity);
				HitParticles hitParticles = modules.get(HitParticles.class);
				if (hitParticles.isEnabled()) hitParticles.spawn(entity);
				HitSound hitSound = modules.get(HitSound.class);
				if (hitSound.isEnabled()) hitSound.onAttack(entity);
			}
			return InteractionResult.PASS;
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
