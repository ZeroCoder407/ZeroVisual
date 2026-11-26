package com.zerovisual.mixin;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.TexturedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** EmoteRig cuts the faces of the player model in two. */
@Mixin(ModelBox.class)
public interface ModelBoxAccessor {
	@Accessor("quadList")
	TexturedQuad[] zerovisual$getQuadList();
}
