package com.zerovisual.util;

/** Implemented by OverlayTexture (OverlayTextureMixin): re-uploads the hurt rows when the HitColor color changed. */
public interface HitColorTexture {
	/** Called from the client tick, never inside a render pass (texture uploads are not allowed there). */
	void zerovisual$refresh();
}
