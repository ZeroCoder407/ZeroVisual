package com.zerovisual;

import com.zerovisual.config.ConfigManager;
import com.zerovisual.gui.HudEditor;
import com.zerovisual.module.ModuleManager;
import com.zerovisual.render.DrawContext;
import com.zerovisual.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/** The Forge mod: builds the modules and forwards the game events to them. */
@Mod(modid = ZeroVisualClient.MOD_ID, name = ZeroVisualClient.NAME, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class ZeroVisualClient {
	public static final String MOD_ID = "zerovisual";
	public static final String NAME = "ZeroVisual";
	public static final Logger LOGGER = LogManager.getLogger(NAME);

	private static ZeroVisualClient instance;

	private ModuleManager modules;
	private ConfigManager config;
	private WaypointManager waypoints;
	private com.zerovisual.account.AccountManager accounts;

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		instance = this;
		com.zerovisual.util.BuildVerifier.start();

		com.zerovisual.module.impl.visuals.Zoom.registerKeyBinding();
		com.zerovisual.module.impl.visuals.Freelook.registerKeyBinding();
		com.zerovisual.module.impl.utilities.Waypoints.registerKeyBinding();
		com.zerovisual.module.impl.cosmetics.EmoteMenu.registerKeyBinding();
		com.zerovisual.module.impl.utilities.MusicMenu.registerKeyBinding();

		modules = new ModuleManager();
		modules.init();
		config = new ConfigManager(modules);
		config.load();
		waypoints = new WaypointManager();
		waypoints.load();
		accounts = new com.zerovisual.account.AccountManager();
		accounts.load();

		HudEditor.register();
		MinecraftForge.EVENT_BUS.register(new com.zerovisual.gui.ScreenHooks());
		com.zerovisual.util.DevScreenshots.registerIfRequested();
		MinecraftForge.EVENT_BUS.register(this);
		// modules with their own event handlers (AutoReconnect...)
		for (com.zerovisual.module.Module module : modules.getModules()) MinecraftForge.EVENT_BUS.register(module);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> config.save(), "ZeroVisual config"));

		LOGGER.info("{} initialized with {} modules", NAME, modules.getModules().size());
	}

	private boolean glSynced;

	/**
	 * While the Forge loading screen runs, mods load in a second GL context, and the GlStateManager cache ends up
	 * saying textures are on when they are off in the window context: every texture would draw as a solid block.
	 * On the first frame the texture switch is set again for real.
	 */
	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (glSynced || event.phase != TickEvent.Phase.START) return;
		glSynced = true;
		GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
		GlStateManager.disableTexture2D();
		GlStateManager.enableTexture2D();
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) modules.onTick();
	}

	@SubscribeEvent
	public void onKey(InputEvent.KeyInputEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (!Keyboard.getEventKeyState()) return;
		if (Keyboard.getEventKey() == mc.gameSettings.keyBindScreenshot.getKeyCode()) {
			com.zerovisual.module.impl.utilities.ScreenshotPlus screenshot = modules.get(com.zerovisual.module.impl.utilities.ScreenshotPlus.class);
			if (screenshot.isEnabled()) screenshot.onScreenshot(mc.mcDataDir);
		}
		if (mc.currentScreen == null) modules.onKeyPress(Keyboard.getEventKey());
	}

	/** Crosshair replaces the vanilla crosshair (the F3 axes stay). */
	@SubscribeEvent
	public void onHudPart(RenderGameOverlayEvent.Pre event) {
		if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
			// the world is finished in the framebuffer, the HUD is not drawn yet
			com.zerovisual.module.impl.visuals.MotionBlur motionBlur = modules.get(com.zerovisual.module.impl.visuals.MotionBlur.class);
			if (motionBlur.isEnabled()) motionBlur.apply(event.resolution.getScaledWidth(), event.resolution.getScaledHeight());
			return;
		}
		if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
		Minecraft mc = Minecraft.getMinecraft();
		com.zerovisual.module.impl.visuals.Crosshair crosshair = modules.get(com.zerovisual.module.impl.visuals.Crosshair.class);
		if (!crosshair.isEnabled() || mc.gameSettings.showDebugInfo) return;
		event.setCanceled(true);
		if (mc.gameSettings.thirdPersonView != 0) return;
		GlStateManager.pushMatrix();
		crosshair.draw(new DrawContext(event.resolution));
		GlStateManager.popMatrix();
		// the vanilla crosshair leaves the GUI texture bound; the next HUD parts expect it
		mc.getTextureManager().bindTexture(net.minecraft.client.gui.Gui.icons);
	}

	/** BlockOutline replaces the black lines around the targeted block. */
	@SubscribeEvent
	public void onBlockHighlight(net.minecraftforge.client.event.DrawBlockHighlightEvent event) {
		com.zerovisual.module.impl.visuals.BlockOutline outline = modules.get(com.zerovisual.module.impl.visuals.BlockOutline.class);
		if (outline.isEnabled() && outline.render(event.target, event.player, event.partialTicks)) event.setCanceled(true);
	}

	@SubscribeEvent
	public void onHud(RenderGameOverlayEvent.Post event) {
		if (event.type == RenderGameOverlayEvent.ElementType.FOOD) {
			com.zerovisual.module.impl.hud.Saturation saturation = modules.get(com.zerovisual.module.impl.hud.Saturation.class);
			Minecraft mc = Minecraft.getMinecraft();
			if (saturation.isEnabled() && mc.thePlayer != null) {
				// the food bar was just drawn above the right_height of the bars before it
				int top = event.resolution.getScaledHeight() - net.minecraftforge.client.GuiIngameForge.right_height + 10;
				saturation.draw(new DrawContext(event.resolution), mc.thePlayer, top, event.resolution.getScaledWidth() / 2 + 91);
				mc.getTextureManager().bindTexture(net.minecraft.client.gui.Gui.icons);
			}
			return;
		}
		if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.gameSettings.hideGUI || mc.gameSettings.showDebugInfo) return;
		DrawContext ctx = new DrawContext(event.resolution);
		GlStateManager.pushMatrix();
		modules.renderHud(ctx, event.partialTicks);
		com.zerovisual.util.BuildVerifier.renderHudMark(ctx);
		com.zerovisual.module.impl.utilities.AutoTool autoTool = modules.get(com.zerovisual.module.impl.utilities.AutoTool.class);
		if (autoTool.isEnabled()) autoTool.render(ctx);
		com.zerovisual.module.impl.hud.LowHealth lowHealth = modules.get(com.zerovisual.module.impl.hud.LowHealth.class);
		if (lowHealth.isEnabled()) lowHealth.render(ctx);
		GlStateManager.popMatrix();
	}

	/** The world effects, in the order of the newer versions' AFTER_ENTITIES. */
	@SubscribeEvent
	public void onWorldRender(net.minecraftforge.client.event.RenderWorldLastEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.getRenderViewEntity() == null) return;
		com.zerovisual.render.WorldRenderContext context = new com.zerovisual.render.WorldRenderContext(event.partialTicks);
		// no pushAttrib here: it restores GL behind the back of the GlStateManager cache, and the game then draws
		// with the wrong textures and blending. Each effect gets the state it expects, and the game's is set back.
		for (com.zerovisual.module.Module module : modules.getModules()) {
			if (!module.isEnabled() || !(module instanceof WorldEffect)) continue;
			GlStateManager.pushMatrix();
			com.zerovisual.render.WorldRenderContext.begin();
			((WorldEffect) module).render(context);
			com.zerovisual.render.WorldRenderContext.end();
			GlStateManager.popMatrix();
		}
		GlStateManager.color(1, 1, 1, 1);
	}

	/** A module that draws in the world every frame. */
	public interface WorldEffect {
		void render(com.zerovisual.render.WorldRenderContext context);
	}

	/** Your hits: TargetHUD, TargetESP, HitParticles, HitSound, KillEffect. */
	@SubscribeEvent
	public void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (event.entityPlayer != mc.thePlayer || !event.entityPlayer.worldObj.isRemote) return;
		com.zerovisual.util.TargetTracker.onAttack(event.target);
		com.zerovisual.module.impl.cosmetics.HitParticles hitParticles = modules.get(com.zerovisual.module.impl.cosmetics.HitParticles.class);
		if (hitParticles.isEnabled()) hitParticles.spawn(event.target);
		com.zerovisual.module.impl.cosmetics.HitSound hitSound = modules.get(com.zerovisual.module.impl.cosmetics.HitSound.class);
		if (hitSound.isEnabled()) hitSound.onAttack(event.target);
	}

	/** Mouse buttons for the CPS counter, the wheel for Zoom (the hotbar does not change while zooming). */
	@SubscribeEvent
	public void onMouse(net.minecraftforge.client.event.MouseEvent event) {
		if (Minecraft.getMinecraft().currentScreen != null) return;
		if (event.buttonstate && event.button >= 0) com.zerovisual.util.CpsCounter.click(event.button);
		if (event.dwheel != 0 && modules.get(com.zerovisual.module.impl.visuals.Zoom.class).onScroll(Math.signum(event.dwheel))) event.setCanceled(true);
	}

	/** Freelook turns the camera, not the player. */
	@SubscribeEvent
	public void onCamera(net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup event) {
		com.zerovisual.module.impl.visuals.Freelook freelook = modules.get(com.zerovisual.module.impl.visuals.Freelook.class);
		if (!freelook.isActive()) return;
		event.yaw = freelook.getYaw() + 180;
		event.pitch = freelook.getPitch();
	}

	/** Ambience: the fog color. */
	@SubscribeEvent
	public void onFogColor(net.minecraftforge.client.event.EntityViewRenderEvent.FogColors event) {
		com.zerovisual.module.impl.visuals.CustomSky sky = modules.get(com.zerovisual.module.impl.visuals.CustomSky.class);
		if (sky.isActive() && Minecraft.getMinecraft().theWorld.provider.getDimensionId() == 0) {
			// the horizon of the custom sky
			float[] fog = sky.fogColor();
			event.red = fog[0];
			event.green = fog[1];
			event.blue = fog[2];
		}
		com.zerovisual.module.impl.visuals.Ambience ambience = modules.get(com.zerovisual.module.impl.visuals.Ambience.class);
		if (!ambience.isEnabled()) return;
		float[] c = ambience.tintFog(event.red, event.green, event.blue);
		event.red = c[0];
		event.green = c[1];
		event.blue = c[2];
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

	public com.zerovisual.account.AccountManager accounts() {
		return accounts;
	}

	public WaypointManager waypoints() {
		return waypoints;
	}
}
