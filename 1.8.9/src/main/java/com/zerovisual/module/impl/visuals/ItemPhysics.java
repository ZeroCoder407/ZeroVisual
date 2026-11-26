package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;

/** Dropped items lie on the ground and somersault in the air. Applied in RenderEntityItemMixin. */
public class ItemPhysics extends Module {
	public ItemPhysics() {
		super("ItemPhysics", "Dropped items lie flat on the ground", Category.VISUALS);
	}
}
