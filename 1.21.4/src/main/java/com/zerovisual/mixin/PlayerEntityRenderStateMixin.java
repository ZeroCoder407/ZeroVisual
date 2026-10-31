package com.zerovisual.mixin;

import com.zerovisual.emote.EmotePose;
import com.zerovisual.util.EmoteState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements EmoteState {
	@Unique
	private final EmotePose zerovisual$pose = new EmotePose();
	@Unique
	private float zerovisual$weight;

	@Override
	public EmotePose zerovisual$pose() {
		return zerovisual$pose;
	}

	@Override
	public float zerovisual$weight() {
		return zerovisual$weight;
	}

	@Override
	public void zerovisual$setWeight(float weight) {
		zerovisual$weight = weight;
	}
}
