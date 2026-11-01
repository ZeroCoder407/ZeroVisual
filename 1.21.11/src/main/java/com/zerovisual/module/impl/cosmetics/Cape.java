package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.BooleanSetting;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/** ZeroVisual cape (and elytra) on your own player. Client-side only: other players do not see it. */
public class Cape extends Module {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(ZeroVisualClient.MOD_ID, "textures/cape/zerovisual.png");
	private static final ClientAsset.ResourceTexture ASSET = new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(ZeroVisualClient.MOD_ID, "cape/zerovisual"));

	public final BooleanSetting elytra = add(new BooleanSetting("Elytra", true));

	public Cape() {
		super("Cape", "ZeroVisual cape (only you can see it)", Category.COSMETICS);
		setEnabled(true);
	}

	public PlayerSkin apply(PlayerSkin original) {
		return new PlayerSkin(original.body(), ASSET, elytra.get() ? ASSET : original.elytra(), original.model(), original.secure());
	}
}
