package com.zerovisual.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** EmoteRig cuts the limbs of the player model in two. */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
	@Accessor("cubes")
	List<ModelPart.Cube> zerovisual$getCubes();

	@Mutable
	@Accessor("cubes")
	void zerovisual$setCubes(List<ModelPart.Cube> cubes);

	@Accessor("children")
	Map<String, ModelPart> zerovisual$getChildren();

	@Mutable
	@Accessor("children")
	void zerovisual$setChildren(Map<String, ModelPart> children);
}
