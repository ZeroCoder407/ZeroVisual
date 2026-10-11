package com.zerovisual.module.impl.visuals;

import com.zerovisual.module.Category;
import com.zerovisual.module.Module;

/** Disables the camera shake on damage. Applied in GameRendererMixin. */
public class NoHurtCam extends Module {
	public NoHurtCam() {
		super("NoHurtCam", "Убирает тряску камеры при получении урона", Category.VISUALS);
	}
}
