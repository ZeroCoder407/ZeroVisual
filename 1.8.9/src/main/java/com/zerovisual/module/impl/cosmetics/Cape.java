package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import net.minecraft.util.ResourceLocation;

/** ZeroVisual cape on your own player. Client-side only: other players do not see it. AbstractClientPlayerMixin. */
public class Cape extends Module {
	public static final ResourceLocation TEXTURE = new ResourceLocation(ZeroVisualClient.MOD_ID, "textures/cape/zerovisual.png");

	public Cape() {
		super("Cape", "ZeroVisual cape (only you can see it)", Category.COSMETICS);
		setEnabled(true);
	}
}
