package com.zerovisual.module.impl.performance;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

/** Spawns only a share of the particles (block breaking, potions, explosions...). */
public class ParticleLimit extends Module {
	public final NumberSetting density = add(new NumberSetting("Density %", 50, 10, 90, 5));

	public ParticleLimit() {
		super("ParticleLimit", "Fewer particles for more FPS", Category.PERFORMANCE);
	}

	/** ParticleManagerMixin: true when this particle is skipped. */
	public boolean skip() {
		return ThreadLocalRandom.current().nextInt(100) >= density.getInt();
	}
}
