package com.zerovisual.module.impl.performance;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;

/**
 * Cheaper end crystal rendering for crystal PvP. Rendering only: crystals break exactly as in vanilla
 * (no instant client-side removal, which would change the fight).
 */
public class CrystalOptimizer extends Module {
	public final BooleanSetting still = add(new BooleanSetting("No spin", true));
	public final BooleanSetting noBeam = add(new BooleanSetting("No beams", true));
	public final BooleanSetting noExplosions = add(new BooleanSetting("No explosion particles", true));

	public CrystalOptimizer() {
		super("CrystalOptimizer", "Crystals without spin, beams and explosion particles (rendering only)", Category.PERFORMANCE);
	}
}
