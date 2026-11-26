package com.zerovisual.module.impl.performance;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.entity.Entity;

/** Entities farther than the distance are not drawn (they still exist and update as usual). */
public class EntityCulling extends Module {
	public final NumberSetting distance = add(new NumberSetting("Distance", 48, 16, 128, 4));
	public final BooleanSetting players = add(new BooleanSetting("Players too", false));

	public EntityCulling() {
		super("EntityCulling", "Does not draw entities farther than the set distance", Category.PERFORMANCE);
	}

	/** EntityRenderDispatcherMixin: true when the entity is too far to be drawn. */
	public boolean hides(Entity entity, double camX, double camY, double camZ) {
		if (entity == mc.thePlayer || entity == mc.getRenderViewEntity()) return false;
		if (entity instanceof net.minecraft.entity.player.EntityPlayer && !players.get()) return false;
		double d = distance.get();
		return entity.getDistanceSq(camX, camY, camZ) > d * d;
	}
}
