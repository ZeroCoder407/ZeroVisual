package com.zerovisual.util;

import com.zerovisual.emote.EmotePose;

/** Implemented by the player render state: the emote pose of this frame, weight 0 when there is none. */
public interface EmoteState {
	EmotePose zerovisual$pose();

	float zerovisual$weight();

	void zerovisual$setWeight(float weight);
}
