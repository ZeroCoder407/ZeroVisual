package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

/** ZeroVisual cape (and elytra) on your own player. Client-side only: other players do not see it. */
public class Cape extends Module {
	public static final Identifier TEXTURE = Identifier.of(ZeroVisualClient.MOD_ID, "textures/cape/zerovisual.png");

	public final BooleanSetting elytra = add(new BooleanSetting("Elytra", true));

	public Cape() {
		super("Cape", "Плащ ZeroVisual (виден только вам)", Category.COSMETICS);
		setEnabled(true);
	}

	public SkinTextures apply(SkinTextures original) {
		return new SkinTextures(original.texture(), original.textureUrl(), TEXTURE,
			elytra.get() ? TEXTURE : original.elytraTexture(), original.model(), original.secure());
	}
}
