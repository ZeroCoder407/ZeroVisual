package com.zerovisual.module.impl.performance;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;

/** A dropped stack is drawn as one item instead of up to five copies. */
public class LiteDrops extends Module {
	public LiteDrops() {
		super("LiteDrops", "Dropped stacks drawn as one item", Category.PERFORMANCE);
	}
}
