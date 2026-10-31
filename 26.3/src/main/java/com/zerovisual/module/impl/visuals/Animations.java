package com.zerovisual.module.impl.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.util.Mth;

/**
 * First-person swing animation styles. Purely visual: the attack itself is unchanged.
 * Applied in HeldItemRendererMixin (swingArm) and LivingEntityMixin (swing speed).
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

	/** Arm lowering while an item is (re)equipped; 0 keeps the item raised. */
	public float adjustEquip(float inverseArmHeight) {
		return noEquip.get() ? 0 : inverseArmHeight;
	}

	/**
	 * Replaces FirstPersonHandsAndItemsRenderer#swingArm (the equip offset is already applied there).
	 *
	 * @param side 1 for the right arm, -1 for the left
	 */
	public void applySwing(PoseStack m, float progress, int side) {
		float sqrt = Mth.sin(Mth.sqrt(progress) * Mth.PI);
		float sin = Mth.sin(progress * Mth.PI);
		float sinSq = Mth.sin(progress * progress * Mth.PI);

		switch (mode.get()) {
			case "Old" -> {
				// vanilla rotation without the position bob, like 1.7
				rotateY(m, side * (45f - 20f * sinSq));
				rotateZ(m, side * -20f * sqrt);
				rotateX(m, -80f * sqrt);
				rotateY(m, side * -45f);
			}
			case "Smooth" -> {
				m.translate(0, 0.04f * sqrt, -0.08f * sin);
				rotateY(m, side * (45f - 25f * sqrt));
				rotateZ(m, side * -12f * sqrt);
				rotateX(m, -55f * sqrt);
				rotateY(m, side * -45f);
			}
			case "Swipe" -> {
				m.translate(side * -0.1f * sqrt, 0.05f * sqrt, -0.1f * sqrt);
				rotateY(m, side * 45f);
				rotateZ(m, side * -70f * sqrt);
				rotateX(m, -25f * sqrt);
				rotateY(m, side * -45f);
			}
			case "Chop" -> {
				m.translate(0, 0.12f * sqrt, -0.05f * sqrt);
				rotateY(m, side * 45f);
				rotateX(m, -100f * sinSq);
				rotateY(m, side * -45f);
			}
			case "Push" -> {
				m.translate(side * -0.05f * sqrt, 0.05f * sqrt, -0.4f * sqrt);
				rotateY(m, side * 45f);
				rotateX(m, -15f * sqrt);
				rotateY(m, side * -45f);
			}
			case "Spin" -> {
				rotateY(m, side * 45f);
				rotateX(m, -360f * progress);
				rotateY(m, side * -45f);
			}
			case "Stab" -> {
				// drawn back first, then thrust straight forward
				float pull = Math.max(0, 1 - progress * 4), thrust = sin * sin;
				m.translate(0, 0.02f * pull, 0.12f * pull - 0.5f * thrust);
				rotateY(m, side * 45f);
				rotateX(m, -28f * sqrt);
				rotateZ(m, side * -10f * thrust);
				rotateY(m, side * -45f);
			}
			case "Slam" -> {
				// raised high over the shoulder, then brought down hard
				float lift = sin * (1 - progress);
				m.translate(0, 0.32f * lift - 0.14f * sinSq, -0.1f * sqrt);
				rotateY(m, side * 45f);
				rotateX(m, 55f * lift - 135f * sinSq);
				rotateY(m, side * -45f);
			}
			case "Backhand" -> {
				// the hand crosses the body and sweeps back out
				m.translate(side * (-0.34f * sin + 0.2f * sinSq), 0.05f * sqrt, -0.14f * sqrt);
				rotateY(m, side * (45f + 75f * sin));
				rotateZ(m, side * 38f * sin);
				rotateX(m, -30f * sqrt);
				rotateY(m, side * -45f);
			}
			case "Twirl" -> {
				// a full turn of the wrist around the blade
				m.translate(0, 0.05f * sin, -0.12f * sin);
				rotateY(m, side * 45f);
				rotateX(m, -32f * sqrt);
				rotateY(m, side * (-45f + 360f * progress));
			}
			default -> {
				// "Vanilla" never reaches here (the mixin keeps the original method)
			}
		}
	}

	private static void rotateX(PoseStack m, float degrees) {
		m.rotate(Axis.XP.rotationDegrees(degrees));
	}

	private static void rotateY(PoseStack m, float degrees) {
		m.rotate(Axis.YP.rotationDegrees(degrees));
	}

	private static void rotateZ(PoseStack m, float degrees) {
		m.rotate(Axis.ZP.rotationDegrees(degrees));
	}
}
