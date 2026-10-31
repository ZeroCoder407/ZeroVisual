package com.zerovisual.mixin;

import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** EmoteRig cuts the limbs of the player model in two. */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
	@Accessor("cuboids")
	List<ModelPart.Cuboid> zerovisual$getCuboids();

	@Mutable
	@Accessor("cuboids")
	void zerovisual$setCuboids(List<ModelPart.Cuboid> cuboids);

	@Accessor("children")
	Map<String, ModelPart> zerovisual$getChildren();

	@Mutable
	@Accessor("children")
	void zerovisual$setChildren(Map<String, ModelPart> children);
}
