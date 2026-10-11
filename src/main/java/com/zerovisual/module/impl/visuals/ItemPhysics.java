package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;

/** Dropped items lie on the ground. Applied in ItemEntityRendererMixin. */
public class ItemPhysics extends Module {
	public ItemPhysics() {
		super("ItemPhysics", "Выброшенные предметы лежат на земле плашмя", Category.VISUALS);
	}
}
