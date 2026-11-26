package com.zerovisual.mixin;

import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** EmoteRig replaces the boxes of a part: its display list is built again. */
@Mixin(ModelRenderer.class)
public interface ModelRendererAccessor {
	@Accessor("compiled")
	void zerovisual$setCompiled(boolean compiled);
}
