package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * First-person swing animation styles. Purely visual: the attack itself is unchanged.
 * Applied in HeldItemRendererMixin (swingArm) and LivingEntityMixin (swing speed).
 */
public class Animations extends Module {
	public final ModeSetting mode = add(new ModeSetting("Style", "Smooth", "Vanilla", "Old", "Smooth", "Swipe", "Chop", "Push", "Spin"));
	public final NumberSetting swingSpeed = add(new NumberSetting("Swing speed", 1, 0.4, 2, 0.1));
	public final BooleanSetting noEquip = add(new BooleanSetting("No re-equip", false));

	public Animations() {
		super("Animations", "Стиль анимации удара и скорость взмаха", Category.VISUALS);
	}

	/** Hand swing duration in ticks for the local player (vanilla is 6). */
	public int adjustSwingDuration(int vanilla) {
		return Math.max(1, Math.round(vanilla / swingSpeed.getFloat()));
	}

	/**
	 * Replaces HeldItemRenderer#swingArm.
	 *
	 * @param side 1 for the right arm, -1 for the left
	 */
	public void applySwing(MatrixStack m, float progress, float equipProgress, int side) {
		if (noEquip.get()) equipProgress = 0;
		float sqrt = MathHelper.sin(MathHelper.sqrt(progress) * MathHelper.PI);
		float sin = MathHelper.sin(progress * MathHelper.PI);
		float sinSq = MathHelper.sin(progress * progress * MathHelper.PI);

		// equip offset, same as vanilla
		m.translate(side * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f);

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
			default -> {
				// "Vanilla" never reaches here (the mixin keeps the original method)
			}
		}
	}

	private static void rotateX(MatrixStack m, float degrees) {
		m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(degrees));
	}

	private static void rotateY(MatrixStack m, float degrees) {
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(degrees));
	}

	private static void rotateZ(MatrixStack m, float degrees) {
		m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
	}
}
