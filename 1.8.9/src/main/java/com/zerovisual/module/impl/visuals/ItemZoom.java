package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.render.Anim;
import com.zerovisual.setting.NumberSetting;

import java.util.Map;
import java.util.WeakHashMap;

/** The item under the mouse in inventories grows a little (HandledScreenMixin). */
public class ItemZoom extends Module {
	public final NumberSetting scale = add(new NumberSetting("Scale", 1.3, 1.1, 1.6, 0.05));

	private final Map<Object, Anim> anims = new WeakHashMap<>();

	public ItemZoom() {
		super("ItemZoom", "The item under the mouse grows in inventories", Category.VISUALS);
	}

	/** Current scale of the item in this slot, animated towards {@link #scale} while hovered. */
	public float scaleFor(Object slot, boolean hovered) {
		Anim anim = anims.computeIfAbsent(slot, s -> new Anim(18f, 0f));
		return 1f + (scale.getFloat() - 1f) * anim.update(hovered);
	}
}
