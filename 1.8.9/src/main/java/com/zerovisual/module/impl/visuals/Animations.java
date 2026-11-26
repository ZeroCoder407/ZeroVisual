package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;

/**
 * First-person swing animation styles. Purely visual: the attack itself is unchanged.
 * Applied in ItemRendererMixin (transformFirstPersonItem) and EntityLivingBaseMixin (swing speed).
 */
public class Animations extends Module {
	public final ModeSetting mode = add(new ModeSetting("Style", "Smooth", "Vanilla", "Old", "Smooth", "Swipe", "Chop", "Push", "Spin", "Stab", "Slam", "Backhand", "Twirl"));
	public final NumberSetting swingSpeed = add(new NumberSetting("Swing speed", 1, 0.4, 2, 0.1));
	public final BooleanSetting noEquip = add(new BooleanSetting("No re-equip", false));

	public Animations() {
		super("Animations", "Swing animation style and speed", Category.VISUALS);
	}

	/** Hand swing duration in ticks for the local player (vanilla is 6). */
	public int adjustSwingDuration(int vanilla) {
		return Math.max(1, Math.round(vanilla / swingSpeed.getFloat()));
	}

	/**
	 * Replaces ItemRenderer#transformFirstPersonItem and the swing bob before it (the 0.4 scale is added by the mixin).
	 *
	 * @param side 1 for the right arm, -1 for the left
	 */
	public void applySwing(float progress, float equipProgress, int side) {
		if (noEquip.get()) equipProgress = 0;
		float sqrt = MathHelper.sin(MathHelper.sqrt_float(progress) * (float) Math.PI);
		float sin = MathHelper.sin(progress * (float) Math.PI);
		float sinSq = MathHelper.sin(progress * progress * (float) Math.PI);

		// equip offset, same as vanilla
		GlStateManager.translate(side * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f);

		switch (mode.get()) {
			case "Old": {
				// vanilla rotation without the position bob, like 1.7
				rotateY(side * (45f - 20f * sinSq));
				rotateZ(side * -20f * sqrt);
				rotateX(-80f * sqrt);
				rotateY(side * -45f);
				break;
			}
			case "Smooth": {
				GlStateManager.translate(0, 0.04f * sqrt, -0.08f * sin);
				rotateY(side * (45f - 25f * sqrt));
				rotateZ(side * -12f * sqrt);
				rotateX(-55f * sqrt);
				rotateY(side * -45f);
				break;
			}
			case "Swipe": {
				GlStateManager.translate(side * -0.1f * sqrt, 0.05f * sqrt, -0.1f * sqrt);
				rotateY(side * 45f);
				rotateZ(side * -70f * sqrt);
				rotateX(-25f * sqrt);
				rotateY(side * -45f);
				break;
			}
			case "Chop": {
				GlStateManager.translate(0, 0.12f * sqrt, -0.05f * sqrt);
				rotateY(side * 45f);
				rotateX(-100f * sinSq);
				rotateY(side * -45f);
				break;
			}
			case "Push": {
				GlStateManager.translate(side * -0.05f * sqrt, 0.05f * sqrt, -0.4f * sqrt);
				rotateY(side * 45f);
				rotateX(-15f * sqrt);
				rotateY(side * -45f);
				break;
			}
			case "Spin": {
				rotateY(side * 45f);
				rotateX(-360f * progress);
				rotateY(side * -45f);
				break;
			}
			case "Stab": {
				// drawn back first, then thrust straight forward
				float pull = Math.max(0, 1 - progress * 4), thrust = sin * sin;
				GlStateManager.translate(0, 0.02f * pull, 0.12f * pull - 0.5f * thrust);
				rotateY(side * 45f);
				rotateX(-28f * sqrt);
				rotateZ(side * -10f * thrust);
				rotateY(side * -45f);
				break;
			}
			case "Slam": {
				// raised high over the shoulder, then brought down hard
				float lift = sin * (1 - progress);
				GlStateManager.translate(0, 0.32f * lift - 0.14f * sinSq, -0.1f * sqrt);
				rotateY(side * 45f);
				rotateX(55f * lift - 135f * sinSq);
				rotateY(side * -45f);
				break;
			}
			case "Backhand": {
				// the hand crosses the body and sweeps back out
				GlStateManager.translate(side * (-0.34f * sin + 0.2f * sinSq), 0.05f * sqrt, -0.14f * sqrt);
				rotateY(side * (45f + 75f * sin));
				rotateZ(side * 38f * sin);
				rotateX(-30f * sqrt);
				rotateY(side * -45f);
				break;
			}
			case "Twirl": {
				// a full turn of the wrist around the blade
				GlStateManager.translate(0, 0.05f * sin, -0.12f * sin);
				rotateY(side * 45f);
				rotateX(-32f * sqrt);
				rotateY(side * (-45f + 360f * progress));
				break;
			}
			default: {
				// "Vanilla" never reaches here (the mixin keeps the original method)
				break;
			}
		}
	}

	private static void rotateX(float degrees) {
		GlStateManager.rotate(degrees, 1, 0, 0);
	}

	private static void rotateY(float degrees) {
		GlStateManager.rotate(degrees, 0, 1, 0);
	}

	private static void rotateZ(float degrees) {
		GlStateManager.rotate(degrees, 0, 0, 1);
	}
}
