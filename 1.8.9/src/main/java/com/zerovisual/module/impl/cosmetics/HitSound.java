package com.zerovisual.module.impl.cosmetics;

import com.zerovisual.ZeroVisualClient;
import com.zerovisual.module.Category;
import com.zerovisual.module.Module;
import com.zerovisual.setting.ModeSetting;
import com.zerovisual.setting.NumberSetting;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

/**
 * A sound when you hit an entity. Played only for you.
 * "Custom" uses the sound event zerovisual:hit.custom, which a resource pack can replace with any .ogg.
 */
public class HitSound extends Module {
	private static final ResourceLocation CUSTOM = new ResourceLocation(ZeroVisualClient.MOD_ID, "hit.custom");

	public final ModeSetting sound = add(new ModeSetting("Sound", "Metallic", "Metallic", "Bell", "Pling", "Bass", "Custom"));
	public final NumberSetting volume = add(new NumberSetting("Volume", 0.8, 0.1, 1, 0.05));

	private final Random random = new Random();

	public HitSound() {
		super("HitSound", "Hit sound on your target (only you hear it)", Category.COSMETICS);
	}

	public void onAttack(Entity target) {
		if (!(target instanceof EntityLivingBase)) return;
		float variation = 0.94f + random.nextFloat() * 0.12f;
		ResourceLocation event;
		float pitch;
		String s = sound.get();
		if (s.equals("Bell")) {
			// 1.8.9 has no bell: the harp an octave up sounds close
			event = new ResourceLocation("note.harp");
			pitch = 2f;
		} else if (s.equals("Pling")) {
			event = new ResourceLocation("note.pling");
			pitch = 1.6f;
		} else if (s.equals("Bass")) {
			event = new ResourceLocation("note.bass");
			pitch = 0.9f;
		} else if (s.equals("Custom")) {
			event = CUSTOM;
			pitch = 1f;
		} else {
			// a short, high anvil clink
			event = new ResourceLocation("random.anvil_land");
			pitch = 1.9f;
		}
		mc.getSoundHandler().playSound(new Master(event, volume.getFloat() * (sound.is("Metallic") ? 0.35f : 1f), Math.min(2f, pitch * variation)));
	}

	/** A sound without a position, at full volume for the listener. */
	private static final class Master extends PositionedSound {
		Master(ResourceLocation sound, float volume, float pitch) {
			super(sound);
			this.volume = volume;
			this.pitch = pitch;
			this.attenuationType = ISound.AttenuationType.NONE;
		}
	}
}
